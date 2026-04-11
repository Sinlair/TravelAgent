package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reranking 服务 - 对检索结果进行重排序
 * 
 * 重排序策略：
 * 1. Cross-Encoder 相关性评分（模拟实现）
 * 2. 上下文相关性
 * 3. 用户偏好匹配
 * 4. 时效性加权
 * 5. MMR 多样性保证
 */
public class RerankingService {

    // Reranking 权重配置
    private static final double RELEVANCE_WEIGHT = 0.35;      // 相关性权重
    private static final double PREFERENCE_WEIGHT = 0.25;     // 偏好匹配权重
    private static final double TIMELINESS_WEIGHT = 0.20;     // 时效性权重
    private static final double DIVERSITY_WEIGHT = 0.20;      // 多样性权重
    
    // MMR 参数
    private static final double MMR_LAMBDA = 0.7;  // 相关性vs多样性权衡

    /**
     * 重排序主方法
     * 
     * @param snippets 候选知识片段
     * @param query 查询文本
     * @param preferences 用户偏好
     * @param topK 返回数量
     * @return 重排序后的结果
     */
    public List<TravelKnowledgeSnippet> rerank(
            List<TravelKnowledgeSnippet> snippets,
            String query,
            List<String> preferences,
            int topK
    ) {
        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }

        // 如果候选集小于 topK，直接返回
        if (snippets.size() <= topK) {
            return snippets;
        }

        // 步骤1: 计算各项分数
        Map<String, Double> relevanceScores = computeRelevanceScores(snippets, query);
        Map<String, Double> preferenceScores = computePreferenceScores(snippets, preferences);
        Map<String, Double> timelinessScores = computeTimelinessScores(snippets);
        
        // 步骤2: 融合分数
        Map<String, Double> finalScores = new HashMap<>();
        for (TravelKnowledgeSnippet snippet : snippets) {
            String id = snippetId(snippet);
            
            double relevance = relevanceScores.getOrDefault(id, 0.0);
            double preference = preferenceScores.getOrDefault(id, 0.0);
            double timeliness = timelinessScores.getOrDefault(id, 0.0);
            
            // 加权融合
            double score = RELEVANCE_WEIGHT * relevance +
                          PREFERENCE_WEIGHT * preference +
                          TIMELINESS_WEIGHT * timeliness;
            
            finalScores.put(id, score);
        }

        // 步骤3: MMR 多样性选择
        return mmrSelect(snippets, finalScores, topK);
    }

    /**
     * 计算相关性分数（模拟 Cross-Encoder）
     * 
     * 实际生产环境应该使用真正的 Cross-Encoder 模型，如：
     * - bge-reranker-large
     * - Cohere Rerank
     * - Jina Reranker
     */
    private Map<String, Double> computeRelevanceScores(
            List<TravelKnowledgeSnippet> snippets,
            String query
    ) {
        Map<String, Double> scores = new HashMap<>();
        String normalizedQuery = normalize(query);
        
        for (TravelKnowledgeSnippet snippet : snippets) {
            String id = snippetId(snippet);
            double score = computeCrossEncoderScore(snippet, normalizedQuery);
            scores.put(id, score);
        }
        
        return scores;
    }

    /**
     * 模拟 Cross-Encoder 分数计算
     * 
     * 真正的 Cross-Encoder 会将 query 和 document 一起输入模型，
     * 计算它们的交互分数。这里用启发式方法模拟。
     */
    private double computeCrossEncoderScore(TravelKnowledgeSnippet snippet, String query) {
        double score = 0.0;
        
        // 1. 标题匹配（权重最高）
        String title = normalize(snippet.title());
        double titleScore = computeSemanticSimilarity(title, query);
        score += 0.4 * titleScore;
        
        // 2. 内容匹配
        String content = normalize(snippet.content());
        double contentScore = computeSemanticSimilarity(content, query);
        score += 0.3 * contentScore;
        
        // 3. 标签匹配
        List<String> tags = snippet.tags();
        double tagScore = computeTagRelevance(tags, query);
        score += 0.15 * tagScore;
        
        // 4. 元数据匹配
        double metadataScore = computeMetadataRelevance(snippet, query);
        score += 0.15 * metadataScore;
        
        return Math.min(score, 1.0);
    }

    /**
     * 计算语义相似度（简化版）
     * 
     * 实际应该使用 Embedding 模型计算余弦相似度
     */
    private double computeSemanticSimilarity(String text1, String text2) {
        if (text1.isBlank() || text2.isBlank()) {
            return 0.0;
        }
        
        // 分词
        Set<String> terms1 = tokenize(text1);
        Set<String> terms2 = tokenize(text2);
        
        if (terms1.isEmpty() || terms2.isEmpty()) {
            return 0.0;
        }
        
        // 计算 Jaccard 相似度
        Set<String> intersection = new HashSet<>(terms1);
        intersection.retainAll(terms2);
        
        Set<String> union = new HashSet<>(terms1);
        union.addAll(terms2);
        
        return (double) intersection.size() / union.size();
    }

    /**
     * 计算标签相关性
     */
    private double computeTagRelevance(List<String> tags, String query) {
        if (tags == null || tags.isEmpty()) {
            return 0.0;
        }
        
        Set<String> queryTerms = tokenize(query);
        int matchCount = 0;
        
        for (String tag : tags) {
            String normalizedTag = normalize(tag);
            for (String term : queryTerms) {
                if (normalizedTag.contains(term) || term.contains(normalizedTag)) {
                    matchCount++;
                    break;
                }
            }
        }
        
        return (double) matchCount / tags.size();
    }

    /**
     * 计算元数据相关性
     */
    private double computeMetadataRelevance(TravelKnowledgeSnippet snippet, String query) {
        double score = 0.0;
        int checks = 0;
        
        // 城市匹配
        if (snippet.city() != null && !snippet.city().isBlank()) {
            if (normalize(snippet.city()).contains(normalize(query)) ||
                normalize(query).contains(normalize(snippet.city()))) {
                score += 1.0;
            }
            checks++;
        }
        
        // 主题匹配
        if (snippet.topic() != null && !snippet.topic().isBlank()) {
            if (normalize(snippet.topic()).contains(normalize(query)) ||
                normalize(query).contains(normalize(snippet.topic()))) {
                score += 0.8;
            }
            checks++;
        }
        
        // 区域匹配
        if (snippet.area() != null && !snippet.area().isBlank()) {
            if (normalize(snippet.area()).contains(normalize(query))) {
                score += 0.6;
            }
            checks++;
        }
        
        return checks > 0 ? score / checks : 0.0;
    }

    /**
     * 计算用户偏好匹配分数
     */
    private Map<String, Double> computePreferenceScores(
            List<TravelKnowledgeSnippet> snippets,
            List<String> preferences
    ) {
        Map<String, Double> scores = new HashMap<>();
        
        if (preferences == null || preferences.isEmpty()) {
            // 没有偏好，所有片段分数相同
            for (TravelKnowledgeSnippet snippet : snippets) {
                scores.put(snippetId(snippet), 0.5);
            }
            return scores;
        }
        
        Set<String> normalizedPrefs = preferences.stream()
                .map(this::normalize)
                .collect(Collectors.toSet());
        
        for (TravelKnowledgeSnippet snippet : snippets) {
            String id = snippetId(snippet);
            double score = computePreferenceMatch(snippet, normalizedPrefs);
            scores.put(id, score);
        }
        
        return scores;
    }

    /**
     * 计算单个片段与用户偏好的匹配度
     */
    private double computePreferenceMatch(
            TravelKnowledgeSnippet snippet,
            Set<String> preferences
    ) {
        double score = 0.0;
        int checks = 0;
        
        // 1. 旅行风格匹配
        List<String> tripStyles = snippet.tripStyleTags();
        if (tripStyles != null && !tripStyles.isEmpty()) {
            int matchCount = 0;
            for (String style : tripStyles) {
                if (preferences.contains(normalize(style))) {
                    matchCount++;
                }
            }
            score += (double) matchCount / tripStyles.size();
            checks++;
        }
        
        // 2. 预算等级匹配
        String budgetLevel = snippet.budgetLevel();
        if (budgetLevel != null && !budgetLevel.isBlank()) {
            if (preferences.contains(normalize(budgetLevel))) {
                score += 1.0;
            }
            checks++;
        }
        
        // 3. 设施匹配
        List<String> facilities = snippet.facilities();
        if (facilities != null && !facilities.isEmpty()) {
            int matchCount = 0;
            for (String facility : facilities) {
                if (preferences.contains(normalize(facility))) {
                    matchCount++;
                }
            }
            score += (double) matchCount / facilities.size();
            checks++;
        }
        
        // 4. 季节匹配（检查当前季节是否在适用季节中）
        List<String> seasons = snippet.season();
        if (seasons != null && !seasons.isEmpty()) {
            String currentSeason = getCurrentSeason();
            if (seasons.contains(currentSeason)) {
                score += 0.8;
            }
            checks++;
        }
        
        return checks > 0 ? score / checks : 0.5;
    }

    /**
     * 计算时效性分数
     * 
     * 考虑因素：
     * 1. 季节匹配（当前季节的片段优先）
     * 2. 质量评分
     * 3. 新鲜度
     */
    private Map<String, Double> computeTimelinessScores(List<TravelKnowledgeSnippet> snippets) {
        Map<String, Double> scores = new HashMap<>();
        String currentSeason = getCurrentSeason();
        
        for (TravelKnowledgeSnippet snippet : snippets) {
            String id = snippetId(snippet);
            double score = 0.0;
            
            // 1. 季节匹配（权重 0.5）
            List<String> seasons = snippet.season();
            if (seasons != null && seasons.contains(currentSeason)) {
                score += 0.5;
            }
            
            // 2. 质量评分（权重 0.3）
            Integer qualityScore = snippet.qualityScore();
            if (qualityScore != null) {
                score += 0.3 * (qualityScore / 100.0);
            }
            
            // 3. 评分（权重 0.2）
            Double rating = snippet.rating();
            if (rating != null) {
                score += 0.2 * (rating / 5.0);
            }
            
            scores.put(id, score);
        }
        
        return scores;
    }

    /**
     * MMR (Maximal Marginal Relevance) 多样性选择
     * 
     * 在相关性和多样性之间取得平衡，避免结果过于单一
     * 
     * MMR = argmax [ λ * Sim(q, di) - (1-λ) * max(Sim(di, dj)) ]
     * 
     * @param snippets 候选片段
     * @param scores 相关性分数
     * @param topK 选择数量
     * @return 选择后的片段列表
     */
    private List<TravelKnowledgeSnippet> mmrSelect(
            List<TravelKnowledgeSnippet> snippets,
            Map<String, Double> scores,
            int topK
    ) {
        List<TravelKnowledgeSnippet> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        
        // 计算片段之间的相似度矩阵
        Map<String, Map<String, Double>> similarityMatrix = computeSimilarityMatrix(snippets);
        
        while (selected.size() < topK && selected.size() < snippets.size()) {
            String bestId = null;
            double bestMMRScore = Double.NEGATIVE_INFINITY;
            
            for (TravelKnowledgeSnippet snippet : snippets) {
                String id = snippetId(snippet);
                
                // 跳过已选择的
                if (selectedIds.contains(id)) {
                    continue;
                }
                
                // 计算 MMR 分数
                double relevanceScore = scores.getOrDefault(id, 0.0);
                double maxSimilarity = 0.0;
                
                // 找到与已选片段的最大相似度
                for (String selectedId : selectedIds) {
                    double sim = similarityMatrix
                            .getOrDefault(id, Map.of())
                            .getOrDefault(selectedId, 0.0);
                    maxSimilarity = Math.max(maxSimilarity, sim);
                }
                
                // MMR 公式
                double mmrScore = MMR_LAMBDA * relevanceScore - 
                                 (1 - MMR_LAMBDA) * maxSimilarity;
                
                if (mmrScore > bestMMRScore) {
                    bestMMRScore = mmrScore;
                    bestId = id;
                }
            }
            
            // 选择最佳片段
            if (bestId != null) {
                final String finalBestId = bestId;
                TravelKnowledgeSnippet selectedSnippet = snippets.stream()
                        .filter(s -> snippetId(s).equals(finalBestId))
                        .findFirst()
                        .orElse(null);
                
                if (selectedSnippet != null) {
                    selected.add(selectedSnippet);
                    selectedIds.add(bestId);
                }
            }
        }
        
        return selected;
    }

    /**
     * 计算片段之间的相似度矩阵
     */
    private Map<String, Map<String, Double>> computeSimilarityMatrix(
            List<TravelKnowledgeSnippet> snippets
    ) {
        Map<String, Map<String, Double>> matrix = new HashMap<>();
        
        for (int i = 0; i < snippets.size(); i++) {
            TravelKnowledgeSnippet s1 = snippets.get(i);
            String id1 = snippetId(s1);
            
            Map<String, Double> simMap = new HashMap<>();
            
            for (int j = 0; j < snippets.size(); j++) {
                if (i == j) continue;
                
                TravelKnowledgeSnippet s2 = snippets.get(j);
                String id2 = snippetId(s2);
                
                double similarity = computeSnippetSimilarity(s1, s2);
                simMap.put(id2, similarity);
            }
            
            matrix.put(id1, simMap);
        }
        
        return matrix;
    }

    /**
     * 计算两个片段之间的相似度
     */
    private double computeSnippetSimilarity(TravelKnowledgeSnippet s1, TravelKnowledgeSnippet s2) {
        double similarity = 0.0;
        
        // 1. 主题相似度（权重 0.4）
        if (s1.topic() != null && s2.topic() != null) {
            if (s1.topic().equals(s2.topic())) {
                similarity += 0.4;
            }
        }
        
        // 2. 标签相似度（权重 0.3）
        Set<String> tags1 = new HashSet<>(s1.tags());
        Set<String> tags2 = new HashSet<>(s2.tags());
        
        if (!tags1.isEmpty() && !tags2.isEmpty()) {
            Set<String> intersection = new HashSet<>(tags1);
            intersection.retainAll(tags2);
            
            Set<String> union = new HashSet<>(tags1);
            union.addAll(tags2);
            
            similarity += 0.3 * ((double) intersection.size() / union.size());
        }
        
        // 3. 区域相似度（权重 0.2）
        if (s1.area() != null && s2.area() != null) {
            if (s1.area().equals(s2.area())) {
                similarity += 0.2;
            }
        }
        
        // 4. 内容相似度（权重 0.1）
        String content1 = normalize(s1.content());
        String content2 = normalize(s2.content());
        
        if (!content1.isBlank() && !content2.isBlank()) {
            similarity += 0.1 * computeSemanticSimilarity(content1, content2);
        }
        
        return Math.min(similarity, 1.0);
    }

    /**
     * 获取当前季节
     */
    private String getCurrentSeason() {
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        
        return switch (month) {
            case 3, 4, 5 -> "春";
            case 6, 7, 8 -> "夏";
            case 9, 10, 11 -> "秋";
            default -> "冬";
        };
    }

    /**
     * 分词（简化版）
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        
        String normalized = text.toLowerCase().trim();
        Set<String> tokens = new HashSet<>();
        
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
     * 标准化文本
     */
    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().trim();
    }

    /**
     * 生成片段唯一 ID
     */
    private String snippetId(TravelKnowledgeSnippet snippet) {
        return snippet.city() + "::" + snippet.topic() + "::" + snippet.title();
    }

    /**
     * 批量重排序
     */
    public Map<String, List<TravelKnowledgeSnippet>> rerankBatch(
            Map<String, List<TravelKnowledgeSnippet>> queries,
            List<String> preferences,
            int topK
    ) {
        Map<String, List<TravelKnowledgeSnippet>> results = new HashMap<>();
        
        for (Map.Entry<String, List<TravelKnowledgeSnippet>> entry : queries.entrySet()) {
            String query = entry.getKey();
            List<TravelKnowledgeSnippet> snippets = entry.getValue();
            
            results.put(query, rerank(snippets, query, preferences, topK));
        }
        
        return results;
    }

    /**
     * 获取权重配置
     */
    public Map<String, Double> getWeights() {
        return Map.of(
                "relevance", RELEVANCE_WEIGHT,
                "preference", PREFERENCE_WEIGHT,
                "timeliness", TIMELINESS_WEIGHT,
                "diversity", DIVERSITY_WEIGHT,
                "mmr_lambda", MMR_LAMBDA
        );
    }
}
