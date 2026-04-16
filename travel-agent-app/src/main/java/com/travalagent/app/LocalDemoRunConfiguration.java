package com.travalagent.app;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Collections;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@Profile("local-demo")
public class LocalDemoRunConfiguration {

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
                return new ChatResponse(List.of(new Generation(new AssistantMessage("local-demo"))));
            }
        };
        return ChatClient.builder(model);
    }
}
