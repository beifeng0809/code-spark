package com.yupi.yuaicodemother.config;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 阿里云百炼 MCP 客户端配置
 *
 * <p>通过 MCP 协议接入百炼 MCP 广场提供的图像生成能力，
 * 用于替代原有直接调用 DashScope SDK 的 Logo 生成逻辑。</p>
 *
 * <h3>如何获取 SSE 地址</h3>
 * <ol>
 *   <li>访问 <a href="https://bailian.console.aliyun.com/#/mcp">百炼 MCP 广场</a></li>
 *   <li>搜索"图像生成"，选择对应服务（如万象文生图）</li>
 *   <li>点击"查看接入信息"，复制 SSE Endpoint 地址</li>
 *   <li>将地址填入 {@code mcp.bailian.image.sse-url}，同时确保 {@code dashscope.api-key} 已配置</li>
 * </ol>
 *
 * <h3>降级策略</h3>
 * <p>若此配置未启用（{@code mcp.bailian.image.enabled=false}），
 * {@link com.yupi.yuaicodemother.langgraph4j.tools.LogoGeneratorTool} 会自动降级到
 * DashScope SDK 直调，不影响 Logo 生成功能。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mcp.bailian.image.enabled", havingValue = "true")
public class BailianMcpConfig {

    /** 百炼 MCP 广场图像生成服务的 SSE Endpoint */
    @Value("${mcp.bailian.image.sse-url}")
    private String sseUrl;

    /** DashScope API Key，百炼 MCP 用同一 Key 鉴权 */
    @Value("${dashscope.api-key:}")
    private String apiKey;

    /** 图像生成耗时较长，超时给足 60 秒 */
    @Value("${mcp.bailian.image.timeout-seconds:60}")
    private int timeoutSeconds;

    private McpClient bailianImageMcpClient;

    @Bean("bailianImageMcpClient")
    public McpClient bailianImageMcpClient() {
        log.info("[MCP] 百炼图像生成客户端初始化，SSE 地址：{}", sseUrl);
        bailianImageMcpClient = new DefaultMcpClient.Builder()
                .transport(new HttpMcpTransport.Builder()
                        .sseUrl(sseUrl)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .logRequests(true)
                        .logResponses(true)
                        .build())
                .toolExecutionTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        return bailianImageMcpClient;
    }

    @PreDestroy
    public void cleanup() {
        if (bailianImageMcpClient != null) {
            try {
                bailianImageMcpClient.close();
                log.info("[MCP] 百炼图像生成客户端已关闭");
            } catch (Exception e) {
                log.warn("[MCP] 关闭百炼客户端时出现异常", e);
            }
        }
    }
}
