package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSelection;
import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务 - 结合 BM25 关键词检索和向量检索
 * 
 * 检索策略：
 * 1. BM25 关键词检索 - 精确匹配地名、专有名词
 * 2. 向量检索 - 语义相似度
 * 3. 加权融合：score = α * bm25_score + β * vector_score
 */
@Component
public class HybridRetrievalService {

    private final VectorStore vectorStore;
    private final RerankingService rerankingService;
    private final QueryUnderstandingService queryUnderstandingService;
    
    // 混合检索权重配置
    private static final double BM25_WEIGHT = 0.4;
    private static final double VECTOR_WEIGHT = 0.6;
    
    // BM25 参数
    private static final double BM25_K1 = 1.5;  // 词频饱和参数
    private static final double BM25_B = 0.75;  // 文档长度归一化参数

    public HybridRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.rerankingService = new RerankingService();
        this.queryUnderstandingService = new QueryUnderstandingService();
    }

    /**
     * 执行混合检索
     * 
     * @param destination 目的地
     * @param preferences 用户偏好
     * @param query 查询文本
     * @param limit 返回结果数量
     * @return 检索结果
     */
    public TravelKnowledgeRetrievalResult hybridSearch(
            String destination,
            List<String> preferences,
            String query,
            int limit
    ) {
        return hybridSearch(destination, preferences, query, limit, List.of());
    }

    /**
     * 执行混合检索（带对话历史）
     * 
     * @param destination 目的地
     * @param preferences 用户偏好
     * @param query 查询文本
     * @param limit 返回结果数量
     * @param conversationHistory 对话历史
     * @return 检索结果
     */
    public TravelKnowledgeRetrievalResult hybridSearch(
            String destination,
            List<String> preferences,
            String query,
            int limit,
            List<String> conversationHistory
    ) {
        // 步骤0: 查询理解
        QueryUnderstandingService.UnderstoodQuery understood = queryUnderstandingService.understand(
                query, conversationHistory
        );
        
        TravelKnowledgeRetrievalSupport.RetrievalPlan plan = 
            TravelKnowledgeRetrievalSupport.plan(destination, preferences, understood.rewrittenQuery());
        
        if (plan.combinedQuery().isBlank() || limit <= 0) {
            return TravelKnowledgeRetrievalSupport.emptyResult(destination, plan, "hybrid+rerank+query");
        }

        // 1. 执行向量检索
        List<Document> vectorResults = performVectorSearch(plan, limit);
        
        // 2. 执行 BM25 关键词检索（基于向量检索结果）
        Map<String, Double> bm25Scores = computeBM25Scores(vectorResults, plan.combinedQuery());
        
        // 3. 获取向量相似度分数
        Map<String, Double> vectorScores = computeVectorScores(vectorResults);
        
        // 4. 融合分数并排序
        List<Document> hybridRanked = hybridRank(vectorResults, bm25Scores, vectorScores);
        
        // 5. 过滤和转换
        List<TravelKnowledgeSnippet> filteredSnippets = new ArrayList<>();
        for (Document doc : hybridRanked) {
            TravelKnowledgeSnippet snippet = TravelKnowledgeRetrievalSupport.enrichSnippet(
                TravelKnowledgeVectorStoreRepository.toSnippetStatic(doc)
            );
            
            // 过滤不匹配的结果
            if (!TravelKnowledgeRetrievalSupport.matchesDestination(snippet, plan.normalizedDestination())) {
                continue;
            }
            if (!TravelKnowledgeRetrievalSupport.matchesTopics(snippet.topic(), plan.inferredTopics())) {
                continue;
            }
            
            filteredSnippets.add(snippet);
        }
        
        // 6. Reranking 重排序（如果候选集足够大）
        if (filteredSnippets.size() > limit) {
            filteredSnippets = rerankingService.rerank(
                filteredSnippets, 
                plan.combinedQuery(), 
                preferences, 
                limit
            );
        }
        
        // 7. 构建最终结果
        return TravelKnowledgeRetrievalSupport.buildResult(
            destination, plan, "hybrid+rerank", filteredSnippets, limit
        );
    }

    /**
     * 执行向量检索
     */
    private List<Document> performVectorSearch(TravelKnowledgeRetrievalSupport.RetrievalPlan plan, int limit) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(plan.combinedQuery())
                .topK(Math.max(limit * 6, 18))  // 扩大候选集
                .similarityThreshold(0.0);
        
        if (plan.filterExpression() != null) {
            requestBuilder.filterExpression(plan.filterExpression());
        }
        
        return vectorStore.similaritySearch(requestBuilder.build());
    }

    /**
     * 计算 BM25 分数
     */
    private Map<String, Double> computeBM25Scores(List<Document> documents, String query) {
        Map<String, Double> scores = new HashMap<>();
        
        if (documents == null || documents.isEmpty()) {
            return scores;
        }
        
        // 分词查询
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return scores;
        }
        
        // 计算文档集合统计信息
        int N = documents.size();  // 文档总数
        Map<String, Integer> df = new HashMap<>();  // 包含词t的文档数
        Map<String, Double> avgDocLength = new HashMap<>();
        
        // 计算平均文档长度
        double totalLength = 0;
        for (Document doc : documents) {
            double docLength = tokenize(doc.getText()).size();
            totalLength += docLength;
        }
        double avgLength = totalLength / N;
        
        // 计算每个词的文档频率
        for (Document doc : documents) {
            List<String> docTerms = tokenize(doc.getText());
            Set<String> uniqueTerms = new HashSet<>(docTerms);
            
            for (String term : uniqueTerms) {
                df.put(term, df.getOrDefault(term, 0) + 1);
            }
        }
        
        // 计算每个文档的 BM25 分数
        for (Document doc : documents) {
            List<String> docTerms = tokenize(doc.getText());
            double docLength = docTerms.size();
            double docScore = 0.0;
            
            for (String queryTerm : queryTerms) {
                if (!df.containsKey(queryTerm)) {
                    continue;
                }
                
                // 词频
                long tf = docTerms.stream().filter(t -> t.equals(queryTerm)).count();
                
                // 逆文档频率 IDF
                int docFreq = df.get(queryTerm);
                double idf = Math.log((N - docFreq + 0.5) / (docFreq + 0.5) + 1.0);
                
                // BM25 公式
                double numerator = tf * (BM25_K1 + 1.0);
                double denominator = tf + BM25_K1 * (1.0 - BM25_B + BM25_B * (docLength / avgLength));
                double termScore = idf * (numerator / denominator);
                
                docScore += termScore;
            }
            
            scores.put(doc.getId(), docScore);
        }
        
        return scores;
    }

    /**
     * 计算向量检索分数
     */
    private Map<String, Double> computeVectorScores(List<Document> documents) {
        Map<String, Double> scores = new HashMap<>();
        
        if (documents == null || documents.isEmpty()) {
            return scores;
        }
        
        // Spring AI 的 Document 已经按相似度排序
        // 我们使用排名来计算分数
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            // 使用倒数排名作为分数
            scores.put(doc.getId(), 1.0 / (i + 1));
        }
        
        return scores;
    }

    /**
     * 混合排序
     */
    private List<Document> hybridRank(
            List<Document> documents,
            Map<String, Double> bm25Scores,
            Map<String, Double> vectorScores
    ) {
        Map<String, Double> hybridScores = new HashMap<>();
        
        for (Document doc : documents) {
            String docId = doc.getId();
            double bm25 = normalizeScore(bm25Scores.getOrDefault(docId, 0.0), bm25Scores.values());
            double vector = normalizeScore(vectorScores.getOrDefault(docId, 0.0), vectorScores.values());
            
            // 加权融合
            double hybridScore = BM25_WEIGHT * bm25 + VECTOR_WEIGHT * vector;
            hybridScores.put(docId, hybridScore);
        }
        
        // 按混合分数排序
        return documents.stream()
                .sorted((d1, d2) -> Double.compare(
                        hybridScores.getOrDefault(d2.getId(), 0.0),
                        hybridScores.getOrDefault(d1.getId(), 0.0)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 分数归一化（Min-Max 归一化）
     */
    private double normalizeScore(double score, Collection<Double> allScores) {
        if (allScores == null || allScores.isEmpty()) {
            return 0.0;
        }
        
        double min = allScores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = allScores.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        
        if (max == min) {
            return 0.5;
        }
        
        return (score - min) / (max - min);
    }

    /**
     * 简单分词（支持中英文）
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        
        String normalized = text.toLowerCase().trim();
        List<String> tokens = new ArrayList<>();
        
        // 按非字母数字和非汉字字符分割
        String[] parts = normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+");
        
        for (String part : parts) {
            if (!part.isBlank() && part.length() > 0) {
                tokens.add(part);
            }
        }
        
        return tokens;
    }

    /**
     * 获取权重配置
     */
    public Map<String, Double> getWeights() {
        return Map.of(
                "bm25", BM25_WEIGHT,
                "vector", VECTOR_WEIGHT
        );
    }

    /**
     * 设置权重（用于实验调优）
     * 注意：这里为了简化使用静态常量，实际应该使用配置类
     */
    public void setWeights(double bm25Weight, double vectorWeight) {
        // 在实际实现中，这应该更新配置
        // 这里仅用于演示
    }
}
