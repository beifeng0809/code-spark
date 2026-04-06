package com.yupi.yuaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;
import com.yupi.yuaicodemother.service.RagDocumentChunkService;
import com.yupi.yuaicodemother.service.RagVectorStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PGVector 存储实现（LangChain4j 官方 EmbeddingStore）
 */
@Service
public class RagVectorStoreServiceImpl implements RagVectorStoreService {

    @Resource
    private EmbeddingModel ragEmbeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> ragEmbeddingStore;

    @Resource
    private RagDocumentChunkService ragDocumentChunkService;

    @Override
    public void upsert(KnowledgeDocument document) {
        if (document == null || document.getId() == null || StrUtil.isBlank(document.getContent())) {
            return;
        }
        // 先删后写，确保同一文档重复更新时不会残留旧片段
        deleteByDocId(document.getId());

        List<TextSegment> segments = ragDocumentChunkService.split(document);
        if (CollUtil.isEmpty(segments)) {
            return;
        }
        List<Embedding> embeddings = ragEmbeddingModel.embedAll(segments).content();
        List<String> embeddingIds = buildEmbeddingIds(document.getId(), segments.size());
        ragEmbeddingStore.addAll(embeddingIds, embeddings, segments);
    }

    @Override
    public void deleteByDocId(Long docId) {
        if (docId == null || docId <= 0) {
            return;
        }
        Filter filter = new MetadataFilterBuilder("docId").isEqualTo(docId);
        ragEmbeddingStore.removeAll(filter);
    }

    @Override
    public List<Long> searchSimilarDocIds(Long appId, Long userId, String query, int limit) {
        if (userId == null || userId <= 0 || StrUtil.isBlank(query)) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, Math.min(limit, 10));
        Embedding queryEmbedding = ragEmbeddingModel.embed(query).content();

        Filter userFilter = new MetadataFilterBuilder("userId").isEqualTo(userId);
        Filter globalFilter = new MetadataFilterBuilder("appId").isEqualTo(-1L);
        Filter appFilter = appId == null
                ? globalFilter
                : new MetadataFilterBuilder("appId").isEqualTo(appId).or(globalFilter);
        Filter scopedFilter = userFilter.and(appFilter);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(Math.max(safeLimit * 4, 12))
                .filter(scopedFilter)
                .build();

        EmbeddingSearchResult<TextSegment> result = ragEmbeddingStore.search(request);
        if (result == null || CollUtil.isEmpty(result.matches())) {
            return new ArrayList<>();
        }
        Set<Long> docIdSet = new LinkedHashSet<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            if (match == null || match.embedded() == null || match.embedded().metadata() == null) {
                continue;
            }
            Long docId = match.embedded().metadata().getLong("docId");
            if (docId == null || docId <= 0) {
                continue;
            }
            docIdSet.add(docId);
            if (docIdSet.size() >= safeLimit) {
                break;
            }
        }
        return new ArrayList<>(docIdSet);
    }

    private List<String> buildEmbeddingIds(Long docId, int segmentCount) {
        List<String> ids = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            ids.add("doc-" + docId + "-chunk-" + i);
        }
        return ids;
    }
}
