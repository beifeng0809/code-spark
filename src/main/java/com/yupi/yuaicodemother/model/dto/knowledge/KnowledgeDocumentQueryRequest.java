package com.yupi.yuaicodemother.model.dto.knowledge;

import com.yupi.yuaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 知识文档查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeDocumentQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String title;

    private String tags;

    private String source;

    private Long appId;

    private Long userId;

    private Integer status;

    private static final long serialVersionUID = 1L;
}
