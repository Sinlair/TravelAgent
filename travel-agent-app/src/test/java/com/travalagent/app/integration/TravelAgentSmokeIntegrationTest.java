package com.travalagent.app.integration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.ai.vectorstore.filter.Filter;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import java.util.Optional;
import java.util.Collections;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.openai.api-key=",
                "spring.ai.mcp.client.enabled=false",
                "spring.ai.mcp.client.initialized=false",
                "travel.agent.tool-provider=LOCAL",
                "travel.agent.memory-provider=SQLITE",
                "travel.agent.memory.milvus.enabled=false",
                "travel.agent.knowledge.vector.enabled=false",
                "travel.agent.amap.api-key=",
                "travel.agent.amap.mock-when-missing-key=true",
                "travel.agent.amap.mock-on-error=true",
                "management.tracing.sampling.probability=0.0",
                "spring.autoconfigure.exclude="
                        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
                        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration"
        }
)
class TravelAgentSmokeIntegrationTest {

    private static final Path SMOKE_DB_PATH = initializeDbPath();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + SMOKE_DB_PATH);
    }

    @Test
    void actuatorHealthReturnsUp() {
        webTestClient().get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void chatReturnsStructuredTravelPlanFromRealApplicationContext() {
        webTestClient().post()
                .uri("/api/conversations/chat")
                .header("Content-Type", "application/json")
                .bodyValue("""
                        {
                          "message": "Plan a 2 day Hangzhou trip with a 900 CNY budget focused on West Lake and local food."
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0000")
                .jsonPath("$.data.agentType").isEqualTo("TRAVEL_PLANNER")
                .jsonPath("$.data.travelPlan").exists()
                .jsonPath("$.data.travelPlan.days[0]").exists()
                .jsonPath("$.data.travelPlan.days[1]").exists()
                .jsonPath("$.data.travelPlan.weatherSnapshot.city").isEqualTo("Hangzhou")
                .jsonPath("$.data.travelPlan.weatherSnapshot.description").exists()
                .jsonPath("$.data.travelPlan.knowledgeRetrieval.retrievalSource").exists()
                .jsonPath("$.data.travelPlan.knowledgeRetrieval.selections[0]").exists();
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private static Path initializeDbPath() {
        try {
            Path directory = Paths.get("target", "smoke").toAbsolutePath();
            Files.createDirectories(directory);
            return directory.resolve("travel-agent-" + UUID.randomUUID() + ".db");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubChatClientConfiguration {

        @Bean
        VectorStore vectorStore() {
            return new VectorStore() {
                @Override
                public void add(List<Document> documents) {}

                @Override
                public void delete(List<String> idList) {}

                @Override
                public void delete(Filter.Expression expression) {}

                @Override
                public List<Document> similaritySearch(SearchRequest request) {
                    return Collections.emptyList();
                }
            };
        }

        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatModel model = new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage("stub"))));
                }
            };
            return ChatClient.builder(model);
        }
    }
}
