package com.yupi.yuaicodemother.model.dto.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识文档新增请求
 */
@Data
public class KnowledgeDocumentAddRequest implements Serializable {

    private String title;

    private String content;

    private String tags;

    private String source;

    private Long appId;

    private Integer priority;

    private static final long serialVersionUID = 1L;
}
