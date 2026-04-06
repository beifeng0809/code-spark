package com.yupi.yuaicodemother.service;

import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;

import java.util.List;

/**
 * PGVector 存储服务
 */
public interface RagVectorStoreService {

    void upsert(KnowledgeDocument document);

    void deleteByDocId(Long docId);

    List<Long> searchSimilarDocIds(Long appId, Long userId, String query, int limit);
}
