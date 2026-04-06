package com.yupi.yuaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yuaicodemother.config.RagChunkingConfig;
import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;
import com.yupi.yuaicodemother.service.RagDocumentChunkService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业级 RAG 文档切分实现，三层递进策略：
 * 1. 结构切分：代码块/表格整体保留；按 Markdown 标题划定段落边界
 * 2. 递归语义切分：超长段落交给 DocumentSplitters.recursive() 处理
 * 3. 上下文注入（可配置）：每个 chunk 携带所属标题，提升检索准确率
 *    依据：Anthropic「Contextual Retrieval」白皮书，准确率提升约 49%
 * 4. 碎片合并：过短 chunk 并入前一个，避免语义碎片
 */
@Service
public class RagDocumentChunkServiceImpl implements RagDocumentChunkService {

    // 代码块：```（可选语言标识）\n ... ```
    private static final Pattern CODE_BLOCK = Pattern.compile("```[^\\n]*\\n.*?```", Pattern.DOTALL);
    // Markdown 表格：标题行 + 分隔行 + 数据行
    private static final Pattern TABLE = Pattern.compile(
            "(\\|.+\\|[ \\t]*\\n)(\\|[-:| \\t]+\\|[ \\t]*\\n)((?:\\|.+\\|[ \\t]*\\n?)+)", Pattern.MULTILINE);
    // Markdown 标题行
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");

    @Resource
    private RagChunkingConfig ragChunkingConfig;

    @Override
    public List<TextSegment> split(KnowledgeDocument document) {
        if (document == null || StrUtil.isBlank(document.getContent())) {
            return new ArrayList<>();
        }
        int maxSize     = safeValue(ragChunkingConfig.getMaxSegmentSize(), 800);
        int overlapSize = safeValue(ragChunkingConfig.getMaxOverlapSize(), 120);
        int minSize     = safeValue(ragChunkingConfig.getMinSegmentSize(), 80);
        boolean injectHeader = Boolean.TRUE.equals(ragChunkingConfig.getAddContextHeader());

        String content = document.getContent().replace("\r\n", "\n");
        DocumentSplitter splitter = DocumentSplitters.recursive(maxSize, overlapSize);

        List<TextSegment> result = new ArrayList<>();
        int chunkIndex = 0;

        for (Block block : parseBlocks(content)) {
            for (String text : toChunkTexts(block, splitter, maxSize, injectHeader)) {
                if (StrUtil.isBlank(text)) continue;
                String trimmed = text.trim();
                // 碎片合并
                if (trimmed.length() < minSize && !result.isEmpty()) {
                    TextSegment prev = result.remove(result.size() - 1);
                    result.add(TextSegment.from(prev.text() + "\n" + trimmed, prev.metadata()));
                } else {
                    result.add(TextSegment.from(trimmed, meta(document, chunkIndex++)));
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // 第一层：结构切分
    // -------------------------------------------------------------------------

    /**
     * 扫描全文，按"保护区间"（代码块/表格）和标题边界拆分为 Block 列表
     */
    private List<Block> parseBlocks(String content) {
        List<Block> blocks = new ArrayList<>();
        // 收集需要整体保留的区间
        List<int[]> protected_ = new ArrayList<>();
        forEachMatch(CODE_BLOCK, content, m -> protected_.add(new int[]{m.start(), m.end(), 0}));
        forEachMatch(TABLE, content, m -> {
            if (!inRange(m.start(), protected_)) {
                protected_.add(new int[]{m.start(), m.end(), 1});
            }
        });
        protected_.sort((a, b) -> Integer.compare(a[0], b[0]));

        int cursor = 0;
        String heading = "";
        for (int[] r : protected_) {
            if (cursor < r[0]) {
                List<Block> textBlocks = splitByHeadings(content.substring(cursor, r[0]), heading);
                if (!textBlocks.isEmpty()) heading = textBlocks.get(textBlocks.size() - 1).heading;
                blocks.addAll(textBlocks);
            }
            String body = content.substring(r[0], r[1]).trim();
            if (StrUtil.isNotBlank(body)) blocks.add(new Block(r[2] == 0 ? Type.CODE : Type.TABLE, heading, body));
            cursor = r[1];
        }
        if (cursor < content.length()) blocks.addAll(splitByHeadings(content.substring(cursor), heading));
        return blocks;
    }

    /**
     * 按 Markdown 标题边界切分普通文本，提取标题文本到 heading，正文去掉标题行
     */
    private List<Block> splitByHeadings(String text, String inheritedHeading) {
        List<Block> blocks = new ArrayList<>();
        if (StrUtil.isBlank(text)) return blocks;
        String currentHeading = inheritedHeading;
        for (String section : text.split("(?m)(?=^#{1,6}\\s+)")) {
            if (StrUtil.isBlank(section)) continue;
            Matcher m = HEADING.matcher(section);
            String body;
            if (m.find()) {
                currentHeading = m.group(2).trim();
                body = section.substring(m.end()).trim();
            } else {
                body = section.trim();
            }
            if (StrUtil.isNotBlank(body)) blocks.add(new Block(Type.TEXT, currentHeading, body));
        }
        return blocks;
    }

    // -------------------------------------------------------------------------
    // 第二 & 第三层：递归切分 + 上下文注入
    // -------------------------------------------------------------------------

    private List<String> toChunkTexts(Block block, DocumentSplitter splitter, int maxSize, boolean injectHeader) {
        if (block.type == Type.CODE) return List.of(truncate(block.body, maxSize * 4));
        if (block.type == Type.TABLE) return List.of(block.body);

        String prefix = (injectHeader && StrUtil.isNotBlank(block.heading)) ? "【" + block.heading + "】\n" : "";
        // 短文本：直接返回
        if (prefix.length() + block.body.length() <= maxSize) return List.of(prefix + block.body);
        // 超长：先切 body，再给每个 sub-chunk 加 prefix
        List<String> result = new ArrayList<>();
        for (TextSegment seg : splitter.split(Document.from(block.body, new Metadata()))) {
            if (seg != null && StrUtil.isNotBlank(seg.text())) result.add(prefix + seg.text().trim());
        }
        return CollUtil.isEmpty(result) ? List.of(prefix + block.body) : result;
    }

    // -------------------------------------------------------------------------
    // 工具
    // -------------------------------------------------------------------------

    private Metadata meta(KnowledgeDocument doc, int chunkIndex) {
        Metadata m = new Metadata();
        m.put("docId",      doc.getId());
        m.put("userId",     doc.getUserId());
        m.put("appId",      doc.getAppId() == null ? -1L : doc.getAppId());
        m.put("chunkIndex", chunkIndex);
        if (StrUtil.isNotBlank(doc.getTitle())) m.put("docTitle", doc.getTitle());
        return m;
    }

    private boolean inRange(int pos, List<int[]> ranges) {
        return ranges.stream().anyMatch(r -> pos >= r[0] && pos < r[1]);
    }

    private void forEachMatch(Pattern p, String text, java.util.function.Consumer<Matcher> action) {
        Matcher m = p.matcher(text);
        while (m.find()) action.accept(m);
    }

    private String truncate(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) + "\n...(已截断)" : text;
    }

    private int safeValue(Integer v, int def) {
        return (v == null || v <= 0) ? def : v;
    }

    // -------------------------------------------------------------------------
    // 内部数据结构
    // -------------------------------------------------------------------------

    private enum Type { CODE, TABLE, TEXT }

    private static class Block {
        final Type type;
        final String heading;
        final String body;
        Block(Type type, String heading, String body) {
            this.type = type; this.heading = heading; this.body = body;
        }
    }
}
