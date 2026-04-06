package com.yupi.yuaicodemother.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.yuaicodemother.annotation.AuthCheck;
import com.yupi.yuaicodemother.common.BaseResponse;
import com.yupi.yuaicodemother.common.DeleteRequest;
import com.yupi.yuaicodemother.common.ResultUtils;
import com.yupi.yuaicodemother.constant.UserConstant;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.exception.ThrowUtils;
import com.yupi.yuaicodemother.model.dto.knowledge.KnowledgeDocumentAddRequest;
import com.yupi.yuaicodemother.model.dto.knowledge.KnowledgeDocumentQueryRequest;
import com.yupi.yuaicodemother.model.dto.knowledge.KnowledgeDocumentUpdateRequest;
import com.yupi.yuaicodemother.model.entity.KnowledgeDocument;
import com.yupi.yuaicodemother.model.entity.User;
import com.yupi.yuaicodemother.service.KnowledgeDocumentService;
import com.yupi.yuaicodemother.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 知识文档管理
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeDocumentController {

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Long> addKnowledge(@RequestBody KnowledgeDocumentAddRequest addRequest,
                                           jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(cn.hutool.core.util.StrUtil.isBlank(addRequest.getTitle()), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(cn.hutool.core.util.StrUtil.isBlank(addRequest.getContent()), ErrorCode.PARAMS_ERROR, "内容不能为空");
        User loginUser = userService.getLoginUser(request);
        KnowledgeDocument doc = new KnowledgeDocument();
        BeanUtil.copyProperties(addRequest, doc);
        doc.setUserId(loginUser.getId());
        doc.setStatus(1);
        if (doc.getPriority() == null) {
            doc.setPriority(0);
        }
        boolean saved = knowledgeDocumentService.save(doc);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(doc.getId());
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateKnowledge(@RequestBody KnowledgeDocumentUpdateRequest updateRequest,
                                                 jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwIf(updateRequest == null || updateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        KnowledgeDocument oldDoc = knowledgeDocumentService.getById(updateRequest.getId());
        ThrowUtils.throwIf(oldDoc == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldDoc.getUserId().equals(loginUser.getId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        KnowledgeDocument doc = new KnowledgeDocument();
        BeanUtil.copyProperties(updateRequest, doc);
        boolean updated = knowledgeDocumentService.updateById(doc);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteKnowledge(@RequestBody DeleteRequest deleteRequest,
                                                 jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        KnowledgeDocument oldDoc = knowledgeDocumentService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(oldDoc == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldDoc.getUserId().equals(loginUser.getId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return ResultUtils.success(knowledgeDocumentService.removeById(deleteRequest.getId()));
    }

    @PostMapping("/my/list/page")
    public BaseResponse<Page<KnowledgeDocument>> listMyKnowledgeByPage(@RequestBody KnowledgeDocumentQueryRequest queryRequest,
                                                                        jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        queryRequest.setUserId(loginUser.getId());
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        QueryWrapper wrapper = knowledgeDocumentService.getQueryWrapper(queryRequest);
        return ResultUtils.success(knowledgeDocumentService.page(Page.of(pageNum, pageSize), wrapper));
    }

    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<KnowledgeDocument>> listKnowledgeByAdmin(@RequestBody KnowledgeDocumentQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        QueryWrapper wrapper = knowledgeDocumentService.getQueryWrapper(queryRequest);
        return ResultUtils.success(knowledgeDocumentService.page(Page.of(pageNum, pageSize), wrapper));
    }
}
