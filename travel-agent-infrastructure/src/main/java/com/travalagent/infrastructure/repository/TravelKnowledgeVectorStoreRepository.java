package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import com.travalagent.infrastructure.config.TravelKnowledgeVectorStoreProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.DropCollectionParam;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnBean(name = "travelKnowledgeMilvusServiceClient")
public class TravelKnowledgeVectorStoreRepository {

    private VectorStore vectorStore;
    private final MilvusServiceClient milvusServiceClient;
    private final EmbeddingModel embeddingModel;
    private final TravelKnowledgeVectorStoreProperties properties;
    private final SmartChunkingService chunkingService;

    @Autowired
    public TravelKnowledgeVectorStoreRepository(
            @Qualifier("travelKnowledgeMilvusServiceClient") MilvusServiceClient milvusServiceClient,
            @Qualifier("travelKnowledgeEmbeddingModel") EmbeddingModel embeddingModel,
            TravelKnowledgeVectorStoreProperties properties
    ) {
        this.milvusServiceClient = milvusServiceClient;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.chunkingService = new SmartChunkingService();
        this.vectorStore = buildMilvusVectorStore(milvusServiceClient, embeddingModel, properties);
    }

    TravelKnowledgeVectorStoreRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.milvusServiceClient = null;
        this.embeddingModel = null;
        this.properties = null;
        this.chunkingService = new SmartChunkingService();
    }

    public int upsert(List<TravelKnowledgeSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return 0;
        }
        
        // 步骤1: 丰富元数据
        List<TravelKnowledgeSnippet> enrichedSnippets = snippets.stream()
                .map(TravelKnowledgeRetrievalSupport::enrichSnippet)
                .toList();
        
        // 步骤2: 智能分块
        List<TravelKnowledgeSnippet> chunkedSnippets = chunkingService.chunkAll(enrichedSnippets);
        
        // 步骤3: 存储到向量数据库
        resetCollectionIfSupported();
        vectorStore.add(chunkedSnippets.stream().map(this::toDocument).toList());
        
        return chunkedSnippets.size();
    }

    public TravelKnowledgeRetrievalResult retrieve(String destination, List<String> preferences, String query, int limit) {
        TravelKnowledgeRetrievalSupport.RetrievalPlan plan = TravelKnowledgeRetrievalSupport.plan(destination, preferences, query);
        if (plan.combinedQuery().isBlank() || limit <= 0) {
            return TravelKnowledgeRetrievalSupport.emptyResult(destination, plan, "vector-store");
        }

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(plan.combinedQuery())
                .topK(Math.max(limit * 6, 18))
                .similarityThreshold(0.0);
        if (plan.filterExpression() != null) {
            requestBuilder.filterExpression(plan.filterExpression());
        }

        List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());
        if (documents == null || documents.isEmpty()) {
            return TravelKnowledgeRetrievalSupport.emptyResult(destination, plan, "vector-store");
        }

        List<TravelKnowledgeSnippet> filtered = new ArrayList<>();
        for (Document document : documents) {
            TravelKnowledgeSnippet snippet = TravelKnowledgeRetrievalSupport.enrichSnippet(toSnippet(document));
            if (!TravelKnowledgeRetrievalSupport.matchesDestination(snippet, plan.normalizedDestination())) {
                continue;
            }
            if (!TravelKnowledgeRetrievalSupport.matchesTopics(snippet.topic(), plan.inferredTopics())) {
                continue;
            }
            filtered.add(snippet);
        }
        return TravelKnowledgeRetrievalSupport.buildResult(destination, plan, "vector-store", filtered, limit);
    }

    private Document toDocument(TravelKnowledgeSnippet snippet) {
        TravelKnowledgeSnippet enriched = TravelKnowledgeRetrievalSupport.enrichSnippet(snippet);
        Map<String, Object> metadata = new LinkedHashMap<>();
        
        // 基础元数据
        metadata.put("city", normalize(enriched.city()));
        metadata.put("topic", normalize(enriched.topic()));
        metadata.put("displayCity", enriched.city());
        metadata.put("displayTopic", enriched.topic());
        metadata.put("cityAliases", String.join(",", enriched.cityAliases()));
        metadata.put("tripStyleTags", String.join(",", enriched.tripStyleTags()));
        metadata.put("title", enriched.title());
        metadata.put("source", enriched.source());
        metadata.put("tags", String.join(",", enriched.tags()));
        metadata.put("schemaSubtype", enriched.schemaSubtype() == null ? "" : enriched.schemaSubtype());
        metadata.put("qualityScore", enriched.qualityScore() == null ? 0 : enriched.qualityScore());
        
        // 增强元数据
        metadata.put("season", String.join(",", enriched.season()));
        metadata.put("budgetLevel", enriched.budgetLevel() == null ? "" : enriched.budgetLevel());
        metadata.put("duration", enriched.duration() == null ? "" : enriched.duration());
        metadata.put("bestTime", enriched.bestTime() == null ? "" : enriched.bestTime());
        metadata.put("crowdLevel", enriched.crowdLevel() == null ? "" : enriched.crowdLevel());
        metadata.put("location", enriched.location() == null ? "" : enriched.location());
        metadata.put("area", enriched.area() == null ? "" : enriched.area());
        metadata.put("rating", enriched.rating() == null ? 0.0 : enriched.rating());
        metadata.put("priceRange", enriched.priceRange() == null ? "" : enriched.priceRange());
        metadata.put("facilities", String.join(",", enriched.facilities()));
        metadata.put("nearbyPOIs", String.join(",", enriched.nearbyPOIs()));
        
        return new Document(
                documentId(enriched),
                enriched.title() + "\n" + enriched.content(),
                metadata
        );
    }

    private TravelKnowledgeSnippet toSnippet(Document document) {
        return toSnippetStatic(document);
    }

    /**
     * 静态方法供外部使用
     */
    static TravelKnowledgeSnippet toSnippetStatic(Document document) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        String title = stringValue(metadata.get("title"));
        String text = document.getText() == null ? "" : document.getText();
        String content = text.startsWith(title + "\n") ? text.substring(title.length() + 1) : text;
        
        return new TravelKnowledgeSnippet(
                stringValue(metadata.getOrDefault("displayCity", metadata.get("city"))),
                stringValue(metadata.getOrDefault("displayTopic", metadata.get("topic"))),
                title,
                content,
                parseTags(metadata.get("tags")),
                stringValue(metadata.get("source")),
                stringValue(metadata.get("schemaSubtype")),
                intValue(metadata.get("qualityScore")),
                parseTags(metadata.get("cityAliases")),
                parseTags(metadata.get("tripStyleTags")),
                // 增强元数据
                parseTags(metadata.get("season")),
                stringValue(metadata.get("budgetLevel")),
                stringValue(metadata.get("duration")),
                stringValue(metadata.get("bestTime")),
                stringValue(metadata.get("crowdLevel")),
                stringValue(metadata.get("location")),
                stringValue(metadata.get("area")),
                doubleValue(metadata.get("rating")),
                stringValue(metadata.get("priceRange")),
                parseTags(metadata.get("facilities")),
                parseTags(metadata.get("nearbyPOIs"))
        );
    }

    private static List<String> parseTags(Object value) {
        if (value == null) {
            return List.of();
        }
        String raw = value.toString();
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String token : raw.split(",")) {
            String cleaned = token.trim();
            if (!cleaned.isBlank() && !tags.contains(cleaned)) {
                tags.add(cleaned);
            }
        }
        return List.copyOf(tags);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private void resetCollectionIfSupported() {
        if (milvusServiceClient == null || embeddingModel == null || properties == null) {
            return;
        }
        try {
            milvusServiceClient.dropCollection(DropCollectionParam.newBuilder()
                    .withDatabaseName(properties.getDatabaseName())
                    .withCollectionName(properties.getCollectionName())
                    .build());
        }
        catch (Exception exception) {
            // Ignore drop failures so first-time seeding still works when the collection does not exist yet.
        }
        this.vectorStore = buildMilvusVectorStore(milvusServiceClient, embeddingModel, properties);
    }

    private VectorStore buildMilvusVectorStore(
            MilvusServiceClient milvusServiceClient,
            EmbeddingModel embeddingModel,
            TravelKnowledgeVectorStoreProperties properties
    ) {
        try {
            MilvusVectorStore store = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                    .databaseName(properties.getDatabaseName())
                    .collectionName(properties.getCollectionName())
                    .embeddingDimension(properties.getEmbeddingDimension())
                    .indexType(IndexType.valueOf(properties.getIndexType()))
                    .metricType(MetricType.valueOf(properties.getMetricType()))
                    .initializeSchema(properties.isInitializeSchema())
                    .indexParameters(properties.getIndexParameters())
                    .build();
            store.afterPropertiesSet();
            return store;
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize travel knowledge vector store", exception);
        }
    }

    private String documentId(TravelKnowledgeSnippet snippet) {
        String raw = normalize(snippet.city()) + "::" + normalize(snippet.topic()) + "::" + normalize(snippet.title());
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
