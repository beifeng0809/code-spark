package com.yupi.yuaicodemother.model.dto.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识文档更新请求
 */
@Data
public class KnowledgeDocumentUpdateRequest implements Serializable {

    private Long id;

    private String title;

    private String content;

    private String tags;

    private String source;

    private Long appId;

    private Integer priority;

    private Integer status;

    private static final long serialVersionUID = 1L;
}
