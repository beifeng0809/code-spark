package com.yupi.yuaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yupi.yuaicodemother.model.dto.knowledge.KnowledgeDocumentQueryRequest;
import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;

import java.util.List;

/**
 * 知识文档服务
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    QueryWrapper getQueryWrapper(KnowledgeDocumentQueryRequest request);

    /**
     * 检索可用于提示词增强的知识片段
     */
    List<KnowledgeDocument> retrieveKnowledge(Long appId, Long userId, String query, int limit);
}
