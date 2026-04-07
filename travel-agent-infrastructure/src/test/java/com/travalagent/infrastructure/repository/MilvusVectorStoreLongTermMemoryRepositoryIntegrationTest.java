package com.travalagent.infrastructure.repository;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.DropCollectionParam;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilvusVectorStoreLongTermMemoryRepositoryIntegrationTest {

    @Test
    void savesAndSearchesMemoryAgainstRealMilvus() throws Exception {
        Assumptions.assumeTrue(isPortReachable("localhost", 19530), "Milvus on localhost:19530 is not reachable");

        String collectionName = "travel_agent_test_" + System.currentTimeMillis();
        MilvusServiceClient client = new MilvusServiceClient(ConnectParam.newBuilder().withUri("http://localhost:19530").build());
        try {
            EmbeddingModel embeddingModel = new KeywordEmbeddingModel();
            MilvusVectorStore vectorStore = MilvusVectorStore.builder(client, embeddingModel)
                    .databaseName("default")
                    .collectionName(collectionName)
                    .embeddingDimension(8)
                    .indexType(IndexType.IVF_FLAT)
                    .metricType(MetricType.COSINE)
                    .indexParameters("{\"nlist\":64}")
                    .initializeSchema(true)
                    .build();
            vectorStore.afterPropertiesSet();

            VectorStoreLongTermMemoryRepository repository = new VectorStoreLongTermMemoryRepository(vectorStore);
            repository.saveMemory(
                    "conversation-1",
                    "TRAVEL_PLANNER",
                    "Hangzhou relaxed food itinerary around West Lake",
                    Map.of("destination", "Hangzhou", "style", "relaxed")
            );
            repository.saveMemory(
                    "conversation-2",
                    "GENERAL",
                    "Beijing business museum schedule near CBD",
                    Map.of("destination", "Beijing", "style", "business")
            );

            List<com.travalagent.domain.model.valobj.LongTermMemoryItem> results = waitForResults(repository, "hangzhou west lake food", 2, Duration.ofSeconds(10));

            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(item -> item.content().contains("Hangzhou relaxed food itinerary around West Lake")));
            assertTrue(results.get(0).content().contains("Hangzhou") || results.get(0).content().contains("West Lake"));
        }
        finally {
            try {
                client.dropCollection(DropCollectionParam.newBuilder()
                        .withDatabaseName("default")
                        .withCollectionName(collectionName)
                        .build());
            }
            catch (Exception ignored) {
            }
            client.close();
        }
    }

    private List<com.travalagent.domain.model.valobj.LongTermMemoryItem> waitForResults(
            VectorStoreLongTermMemoryRepository repository,
            String query,
            int limit,
            Duration timeout
    ) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        List<com.travalagent.domain.model.valobj.LongTermMemoryItem> latest = List.of();
        while (Instant.now().isBefore(deadline)) {
            latest = repository.searchRelevant(query, limit);
            if (!latest.isEmpty()) {
                return latest;
            }
            Thread.sleep(500);
        }
        return latest;
    }

    private boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private static final class KeywordEmbeddingModel implements EmbeddingModel {

        private static final List<String> TERMS = List.of("hangzhou", "beijing", "food", "museum", "relaxed", "business", "west", "lake");

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(this::toVector)
                    .map(vector -> new Embedding(vector, 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return toVector(document.getText());
        }

        @Override
        public int dimensions() {
            return TERMS.size();
        }

        private float[] toVector(String text) {
            String lower = text == null ? "" : text.toLowerCase();
            float[] vector = new float[TERMS.size()];
            for (int i = 0; i < TERMS.size(); i++) {
                String term = TERMS.get(i);
                if (lower.contains(term)) {
                    vector[i] = 1.0f;
                }
            }
            return vector;
        }
    }
}