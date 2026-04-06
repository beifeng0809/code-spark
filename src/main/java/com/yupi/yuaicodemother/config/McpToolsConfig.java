package com.yupi.yuaicodemother.config;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.mcp.client.McpToolProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Fetch MCP 工具配置
 *
 * <p>接入 {@code @modelcontextprotocol/server-fetch}，让 AI 在生成代码时
 * 可以主动抓取网页内容（MDN 文档、npm 包说明、组件库 API 等），
 * 而非完全依赖训练数据。</p>
 *
 * <p><b>启动方式（二选一）：</b></p>
 * <ul>
 *   <li><b>Stdio 模式（默认）：</b>应用启动时自动通过 npx 拉起 MCP 进程，无需额外操作。
 *       需要本机已安装 Node.js 18+。</li>
 *   <li><b>HTTP/SSE 模式：</b>手动启动 MCP 服务后，配置 {@code mcp.fetch.sse-url} 即可切换。
 *       启动命令：{@code npx -y @modelcontextprotocol/server-fetch --port 3001}</li>
 * </ul>
 *
 * <p><b>关闭方式：</b>将 {@code mcp.fetch.enabled} 设为 {@code false} 即完全禁用，
 * 不影响其他任何功能。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mcp.fetch.enabled", havingValue = "true", matchIfMissing = false)
public class McpToolsConfig {

    /** 可选：外部 HTTP/SSE 模式的 MCP 服务地址，不配置则使用 Stdio 模式 */
    @Value("${mcp.fetch.sse-url:}")
    private String sseUrl;

    /** Stdio 模式下调用的命令（默认通过 npx 拉起） */
    @Value("${mcp.fetch.command:npx}")
    private String command;

    @Value("${mcp.fetch.args:-y,@modelcontextprotocol/server-fetch}")
    private String args;

    /** 单次工具调用超时（秒） */
    @Value("${mcp.fetch.timeout-seconds:30}")
    private int timeoutSeconds;

    private McpClient fetchMcpClient;

    /**
     * Fetch MCP 客户端
     *
     * <p>优先使用 HTTP/SSE 模式（{@code mcp.fetch.sse-url} 有值时），
     * 否则退回 Stdio 模式，由 JVM 自动管理子进程生命周期。</p>
     */
    @Bean
    public McpClient fetchMcpClient() {
        McpTransport transport;
        if (sseUrl != null && !sseUrl.isBlank()) {
            log.info("[MCP] Fetch 使用 HTTP/SSE 模式，地址：{}", sseUrl);
            transport = new HttpMcpTransport.Builder()
                    .sseUrl(sseUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } else {
            List<String> cmd = buildCommand();
            log.info("[MCP] Fetch 使用 Stdio 模式，命令：{}", cmd);
            transport = new StdioMcpTransport.Builder()
                    .command(cmd)
                    .logEvents(true)
                    .build();
        }
        fetchMcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        return fetchMcpClient;
    }

    /**
     * 将 Fetch MCP 工具暴露为 {@link ToolProvider}
     *
     * <p>LangChain4j Spring Boot Starter 会自动发现 {@code ToolProvider} bean
     * 并将其注入到所有通过 {@code AiServices} 构建的 AI 服务中。</p>
     *
     * <p>Fetch MCP 提供的工具：
     * <ul>
     *   <li>{@code fetch} —— 抓取指定 URL 的网页内容，返回原始 HTML</li>
     *   <li>{@code fetch_markdown} —— 抓取 URL 并转换为 Markdown 格式，更适合 LLM 阅读</li>
     * </ul>
     * </p>
     */
    @Bean
    public ToolProvider fetchMcpToolProvider(McpClient fetchMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(List.of(fetchMcpClient))
                .build();
    }

    /** 应用关闭时释放 MCP 子进程/连接 */
    @PreDestroy
    public void cleanup() {
        if (fetchMcpClient != null) {
            try {
                fetchMcpClient.close();
                log.info("[MCP] Fetch 客户端已关闭");
            } catch (Exception e) {
                log.warn("[MCP] 关闭 Fetch 客户端时出现异常", e);
            }
        }
    }

    private List<String> buildCommand() {
        // args 格式："-y,@modelcontextprotocol/server-fetch"
        String[] argArray = args.split(",");
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(command);
        for (String arg : argArray) {
            String trimmed = arg.trim();
            if (!trimmed.isEmpty()) cmd.add(trimmed);
        }
        return cmd;
    }
}
