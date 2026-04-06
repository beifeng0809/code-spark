package com.yupi.yuaicodemother.controller;

import com.yupi.yuaicodemother.common.BaseResponse;
import com.yupi.yuaicodemother.common.ResultUtils;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.exception.ThrowUtils;
import com.yupi.yuaicodemother.langgraph4j.CheckpointedWorkflowService;
import com.yupi.yuaicodemother.langgraph4j.state.WorkflowContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 工作流 Checkpoint 接口
 *
 * <p>演示 LangGraph4j 有状态工作流的核心用法：
 * <ul>
 *   <li>异步启动：提交任务后立即返回，后台用虚拟线程执行</li>
 *   <li>状态查询：通过 appId（即 threadId）随时读取 MemorySaver 中的最新快照</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/workflow/checkpoint")
@Slf4j
public class WorkflowCheckpointController {

    @Resource
    private CheckpointedWorkflowService checkpointedWorkflowService;

    /**
     * 异步启动有状态工作流
     *
     * <p>接口立即返回，工作流在后台虚拟线程中执行。
     * 每个节点完成后状态自动写入 MemorySaver，可通过 {@code /state} 接口轮询进度。</p>
     *
     * @param appId  应用 ID，同一 appId 的多次调用共享同一图状态线程
     * @param prompt 用户提示词
     */
    @PostMapping("/start")
    public BaseResponse<Map<String, Object>> start(
            @RequestParam Long appId,
            @RequestParam String prompt) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "appId 无效");
        ThrowUtils.throwIf(prompt == null || prompt.isBlank(), ErrorCode.PARAMS_ERROR, "prompt 不能为空");

        checkpointedWorkflowService.startAsync(appId, prompt);

        return ResultUtils.success(Map.of(
                "appId", appId,
                "threadId", "app-" + appId,
                "status", "started",
                "message", "工作流已异步启动，可通过 /state 接口轮询执行进度"
        ));
    }

    /**
     * 查询工作流最新状态快照
     *
     * <p>从 MemorySaver 中读取该 appId 上次执行到的最新节点状态，
     * 包含当前步骤、生成的代码目录、质量检查结果等上下文信息。</p>
     *
     * @param appId 应用 ID
     */
    @GetMapping("/state")
    public BaseResponse<WorkflowContext> getState(@RequestParam Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "appId 无效");

        Optional<WorkflowContext> state = checkpointedWorkflowService.getLatestState(appId);
        return ResultUtils.success(state.orElse(null));
    }
}
