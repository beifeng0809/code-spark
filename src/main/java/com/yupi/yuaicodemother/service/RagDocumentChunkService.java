package com.yupi.yuaicodemother.service;

import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * RAG 文档切分服务
 */
public interface RagDocumentChunkService {

    /**
     * 将文档切分为语义片段
     */
    List<TextSegment> split(KnowledgeDocument document);
}
