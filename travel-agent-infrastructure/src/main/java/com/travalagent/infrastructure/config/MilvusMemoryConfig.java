package com.travalagent.infrastructure.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MilvusMemoryConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "travel.agent.memory.milvus", name = "enabled", havingValue = "true")
    MilvusServiceClient milvusServiceClient(MilvusMemoryProperties properties) {
        ConnectParam.Builder builder = ConnectParam.newBuilder().withUri(properties.getUri());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            builder.withAuthorization(properties.getUsername(), properties.getPassword());
        }
        return new MilvusServiceClient(builder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.agent.memory.milvus", name = "enabled", havingValue = "true")
    VectorStore milvusVectorStore(
            MilvusServiceClient milvusServiceClient,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            MilvusMemoryProperties properties
    ) {
        return MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .databaseName(properties.getDatabaseName())
                .collectionName(properties.getCollectionName())
                .embeddingDimension(properties.getEmbeddingDimension())
                .indexType(IndexType.valueOf(properties.getIndexType()))
                .metricType(MetricType.valueOf(properties.getMetricType()))
                .initializeSchema(properties.isInitializeSchema())
                .indexParameters(properties.getIndexParameters())
                .build();
    }
}