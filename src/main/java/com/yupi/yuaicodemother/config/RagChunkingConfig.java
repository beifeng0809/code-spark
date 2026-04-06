package com.yupi.yuaicodemother.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 文档切分配置
 */
@Configuration
@ConfigurationProperties(prefix = "rag.chunk")
@Data
public class RagChunkingConfig {

    /**
     * 单个 chunk 最大字符数。
     * 推荐值：800（约 400 token），处于主流 embedding 模型的最优输入区间。
     */
    private Integer maxSegmentSize = 800;

    /**
     * 相邻 chunk 之间的重叠字符数。
     * 推荐值：120（约 15% 重叠率），保证跨 chunk 上下文连续性，降低边界截断的信息损失。
     */
    private Integer maxOverlapSize = 120;

    /**
     * chunk 最小有效字符数。
     * 低于此值的碎片 chunk 会被合并到前一个，避免噪声污染向量索引。
     * 推荐值：80。
     */
    private Integer minSegmentSize = 80;

    /**
     * 是否向每个 chunk 注入所属标题前缀（Contextual Header Injection）。
     * 依据：Anthropic「Contextual Retrieval」白皮书，检索准确率提升约 49%。
     */
    private Boolean addContextHeader = true;
}
