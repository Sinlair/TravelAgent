package com.travalagent.infrastructure.config;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration(proxyBeanMethods = false)
public class SimpleVectorStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "travel.agent.memory.simple", name = "enabled", havingValue = "true")
    SimpleVectorStore simpleVectorStore(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel, SimpleMemoryProperties properties) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(properties.getFilePath());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (file.exists()) {
            vectorStore.load(file);
        }
        return vectorStore;
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.agent.memory.simple", name = "enabled", havingValue = "true")
    SimpleVectorStorePersistence simpleVectorStorePersistence(SimpleVectorStore vectorStore, SimpleMemoryProperties properties) {
        return new SimpleVectorStorePersistence(vectorStore, properties);
    }

    static final class SimpleVectorStorePersistence {

        private final SimpleVectorStore vectorStore;
        private final SimpleMemoryProperties properties;

        SimpleVectorStorePersistence(SimpleVectorStore vectorStore, SimpleMemoryProperties properties) {
            this.vectorStore = vectorStore;
            this.properties = properties;
        }

        @PreDestroy
        void persist() {
            File file = new File(properties.getFilePath());
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            vectorStore.save(file);
        }
    }
}