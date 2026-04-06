package com.yupi.yuaicodemother.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.yupi.yuaicodemother.langgraph4j.model.ImageResource;
import com.yupi.yuaicodemother.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logo 图片生成工具
 *
 * <p><b>调用策略（双源降级）：</b></p>
 * <ol>
 *   <li><b>优先</b>：通过百炼 MCP 协议调用图像生成服务
 *       （需配置 {@code mcp.bailian.image.enabled=true}）</li>
 *   <li><b>降级</b>：直接调用 DashScope Java SDK
 *       （仅需 {@code dashscope.api-key}，始终可用）</li>
 * </ol>
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    /** 百炼 MCP 客户端，未启用时为 null */
    @Autowired(required = false)
    @Qualifier("bailianImageMcpClient")
    private McpClient bailianImageMcpClient;

    /** 百炼 MCP 广场图像生成工具名，从服务详情页查看 */
    @Value("${mcp.bailian.image.tool-name:wanx_text_to_image}")
    private String mcpToolName;

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        if (bailianImageMcpClient != null) {
            log.info("[Logo] 使用百炼 MCP 生成 Logo");
            List<ImageResource> result = generateViaMcp(description);
            if (!result.isEmpty()) {
                return result;
            }
            log.warn("[Logo] 百炼 MCP 返回空结果，降级到 DashScope SDK");
        }
        return generateViaDashScopeSdk(description);
    }

    // -------------------------------------------------------------------------
    // MCP 调用
    // -------------------------------------------------------------------------

    private List<ImageResource> generateViaMcp(String description) {
        try {
            String prompt = buildPrompt(description);
            String arguments = JSONUtil.createObj()
                    .set("prompt", prompt)
                    .set("size", "512*512")
                    .set("n", 1)
                    .toString();
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(mcpToolName)
                    .arguments(arguments)
                    .build();
            String responseText = bailianImageMcpClient.executeTool(request).text();
            log.debug("[Logo][MCP] 响应：{}", responseText);
            return parseImageUrls(responseText, description);
        } catch (Exception e) {
            log.error("[Logo][MCP] 调用失败：{}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 从 MCP 响应中提取图片 URL。
     *
     * <p>百炼 MCP 响应格式可能为：
     * <ul>
     *   <li>{@code {"images":[{"url":"..."}]}}</li>
     *   <li>{@code {"url":"..."}}</li>
     *   <li>纯 URL 字符串</li>
     * </ul>
     * 逐级尝试解析，保证兼容性。</p>
     */
    private List<ImageResource> parseImageUrls(String responseText, String description) {
        List<ImageResource> result = new ArrayList<>();
        if (StrUtil.isBlank(responseText)) {
            return result;
        }
        try {
            if (JSONUtil.isTypeJSON(responseText)) {
                JSONObject json = JSONUtil.parseObj(responseText);
                // 格式1：{"images":[{"url":"..."}]}
                if (json.containsKey("images")) {
                    JSONArray images = json.getJSONArray("images");
                    for (int i = 0; i < images.size(); i++) {
                        String url = images.getJSONObject(i).getStr("url");
                        addIfValid(result, url, description);
                    }
                }
                // 格式2：{"url":"..."}
                if (result.isEmpty() && json.containsKey("url")) {
                    addIfValid(result, json.getStr("url"), description);
                }
            } else if (responseText.trim().startsWith("http")) {
                // 纯 URL 字符串
                addIfValid(result, responseText.trim(), description);
            }
        } catch (Exception e) {
            log.warn("[Logo][MCP] 解析响应失败：{}", e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // DashScope SDK 降级
    // -------------------------------------------------------------------------

    private List<ImageResource> generateViaDashScopeSdk(String description) {
        List<ImageResource> result = new ArrayList<>();
        try {
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(buildPrompt(description))
                    .size("512*512")
                    .n(1)
                    .build();
            ImageSynthesisResult sdkResult = new ImageSynthesis().call(param);
            if (sdkResult != null && sdkResult.getOutput() != null
                    && sdkResult.getOutput().getResults() != null) {
                for (Map<String, String> item : sdkResult.getOutput().getResults()) {
                    addIfValid(result, item.get("url"), description);
                }
            }
        } catch (Exception e) {
            log.error("[Logo][SDK] 生成失败：{}", e.getMessage(), e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    private String buildPrompt(String description) {
        return "生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：" + description;
    }

    private void addIfValid(List<ImageResource> list, String url, String description) {
        if (StrUtil.isNotBlank(url)) {
            list.add(ImageResource.builder()
                    .category(ImageCategoryEnum.LOGO)
                    .description(description)
                    .url(url)
                    .build());
        }
    }
}
