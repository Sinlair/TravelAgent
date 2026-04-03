package com.xx2201.travel.agent.infrastructure.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class InfrastructureConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Qualifier("travelKnowledgeEmbeddingModel")
    EmbeddingModel travelKnowledgeEmbeddingModel(TravelKnowledgeVectorStoreProperties properties) {
        return new TravelKnowledgeHashEmbeddingModel(properties.getEmbeddingDimension());
    }

    @Bean(name = "travelKnowledgeMilvusServiceClient", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "travel.agent.knowledge.vector", name = "enabled", havingValue = "true")
    MilvusServiceClient travelKnowledgeMilvusServiceClient(TravelKnowledgeVectorStoreProperties properties) {
        ConnectParam.Builder builder = ConnectParam.newBuilder().withUri(properties.getUri());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            builder.withAuthorization(properties.getUsername(), properties.getPassword());
        }
        return new MilvusServiceClient(builder.build());
    }
}
