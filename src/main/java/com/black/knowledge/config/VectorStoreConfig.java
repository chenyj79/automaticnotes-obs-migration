package com.black.knowledge.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.JedisPooled;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;

/**
 * VectorStore 配置
 * 优先 Redis Stack，无 RediSearch 时降级为内存存储
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public JedisPooled jedisPooled(RedisProperties redisProperties) {
        String host = redisProperties.getHost();
        int port = redisProperties.getPort();
        String password = redisProperties.getPassword();

        if (password != null && !password.isEmpty()) {
            return new JedisPooled(host, port, null, password);
        } else {
            return new JedisPooled(host, port);
        }
    }

    /**
     * Redis VectorStore（需要 RediSearch），默认启用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.vectorstore.enabled", havingValue = "true", matchIfMissing = true)
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("knowledge-index")
                .prefix("knowledge:")
                .metadataFields(
                        MetadataField.tag("frameworkId"),
                        MetadataField.tag("pointId"),
                        MetadataField.tag("title"))
                .initializeSchema(true)
                .build();
    }

    /**
     * 降级：SimpleVectorStore 内存向量存储（无需 Redis Stack）
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.vectorstore.enabled", havingValue = "false")
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
