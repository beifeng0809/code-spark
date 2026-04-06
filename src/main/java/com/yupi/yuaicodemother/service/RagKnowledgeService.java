package com.yupi.yuaicodemother.service;

import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;

import java.util.List;

/**
 * RAG 知识增强服务
 */
public interface RagKnowledgeService {

    List<KnowledgeDocument> searchKnowledge(Long appId, Long userId, String userPrompt, int limit);

    String buildKnowledgePrompt(List<KnowledgeDocument> knowledgeList);
}
