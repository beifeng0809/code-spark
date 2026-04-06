package com.yupi.yuaicodemother.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PGVector 向量存储配置（LangChain4j 官方实现）
 */
@Configuration
@ConfigurationProperties(prefix = "rag.pgvector")
@Data
public class RagPgVectorDataSourceConfig {

    private String host = "localhost";

    private Integer port = 5432;

    private String database;

    private String username;

    private String password;

    private String table = "rag_knowledge_embedding";

    private Integer dimension = 1536;

    @Bean
    public EmbeddingStore<TextSegment> ragEmbeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(username)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(true)
                .build();
    }
}
