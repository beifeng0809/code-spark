package com.yupi.yuaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.mapper.KnowledgeDocumentMapper;
import com.yupi.yuaicodemother.model.dto.knowledge.KnowledgeDocumentQueryRequest;
import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;
import com.yupi.yuaicodemother.service.KnowledgeDocumentService;
import com.yupi.yuaicodemother.service.RagVectorStoreService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识文档服务实现
 */
@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    @Resource
    private RagVectorStoreService ragVectorStoreService;

    @Override
    public QueryWrapper getQueryWrapper(KnowledgeDocumentQueryRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        return QueryWrapper.create()
                .eq("id", request.getId())
                .like("title", request.getTitle())
                .like("tags", request.getTags())
                .eq("source", request.getSource())
                .eq("appId", request.getAppId())
                .eq("userId", request.getUserId())
                .eq("status", request.getStatus())
                .orderBy(request.getSortField(), "ascend".equals(request.getSortOrder()));
    }

    @Override
    public List<KnowledgeDocument> retrieveKnowledge(Long appId, Long userId, String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        if (userId == null || userId <= 0 || StrUtil.isBlank(query)) {
            return new ArrayList<>();
        }

        // 先从 PGVector 检索最相关文档 ID
        List<Long> docIds = ragVectorStoreService.searchSimilarDocIds(appId, userId, query, safeLimit);
        if (CollUtil.isEmpty(docIds)) {
            return new ArrayList<>();
        }

        // 批量加载文档并按向量检索顺序重排
        List<KnowledgeDocument> docs = this.listByIds(docIds);
        if (CollUtil.isEmpty(docs)) {
            return new ArrayList<>();
        }
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < docIds.size(); i++) {
            orderMap.put(docIds.get(i), i);
        }
        return docs.stream()
                .filter(doc -> doc != null
                        && Integer.valueOf(1).equals(doc.getStatus())
                        && doc.getUserId() != null
                        && doc.getUserId().equals(userId))
                .sorted((a, b) -> Integer.compare(
                        orderMap.getOrDefault(a.getId(), Integer.MAX_VALUE),
                        orderMap.getOrDefault(b.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    @Override
    public boolean save(KnowledgeDocument entity) {
        boolean saved = super.save(entity);
        if (saved) {
            ragVectorStoreService.upsert(entity);
        }
        return saved;
    }

    @Override
    public boolean updateById(KnowledgeDocument entity) {
        boolean updated = super.updateById(entity);
        if (updated && entity != null && entity.getId() != null) {
            KnowledgeDocument latest = this.getById(entity.getId());
            if (latest != null && Integer.valueOf(1).equals(latest.getStatus())) {
                ragVectorStoreService.upsert(latest);
            } else {
                ragVectorStoreService.deleteByDocId(entity.getId());
            }
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean removed = super.removeById(id);
        if (removed && id != null) {
            long docId = Long.parseLong(id.toString());
            ragVectorStoreService.deleteByDocId(docId);
        }
        return removed;
    }
}
