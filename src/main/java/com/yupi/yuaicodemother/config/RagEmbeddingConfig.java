package com.yupi.yuaicodemother.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Embedding 配置
 */
@Configuration
@ConfigurationProperties(prefix = "rag.embedding")
@Data
public class RagEmbeddingConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer dimension = 1536;

    private Integer timeoutMs = 8000;

    @Bean
    public EmbeddingModel ragEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(java.time.Duration.ofMillis(timeoutMs == null ? 8000 : timeoutMs))
                .build();
    }
}
