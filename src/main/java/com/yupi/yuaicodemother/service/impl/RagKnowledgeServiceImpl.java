package com.yupi.yuaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;
import com.yupi.yuaicodemother.service.KnowledgeDocumentService;
import com.yupi.yuaicodemother.service.RagKnowledgeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 知识增强服务实现
 */
@Service
public class RagKnowledgeServiceImpl implements RagKnowledgeService {

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Override
    public List<KnowledgeDocument> searchKnowledge(Long appId, Long userId, String userPrompt, int limit) {
        return knowledgeDocumentService.retrieveKnowledge(appId, userId, userPrompt, limit);
    }

    @Override
    public String buildKnowledgePrompt(List<KnowledgeDocument> knowledgeList) {
        if (CollUtil.isEmpty(knowledgeList)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 企业知识库参考（RAG）\n");
        sb.append("请优先参考以下知识进行实现，若与用户需求冲突，以用户需求为准。\n");
        int index = 1;
        for (KnowledgeDocument doc : knowledgeList) {
            if (doc == null || StrUtil.isBlank(doc.getContent())) {
                continue;
            }
            sb.append("### ").append(index).append(". ")
                    .append(StrUtil.blankToDefault(doc.getTitle(), "未命名文档")).append("\n");
            if (StrUtil.isNotBlank(doc.getTags())) {
                sb.append("标签：").append(doc.getTags()).append("\n");
            }
            sb.append(doc.getContent()).append("\n\n");
            index++;
        }
        return sb.toString();
    }
}
