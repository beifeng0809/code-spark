package com.yupi.yuaicodemother.langgraph4j;

import com.yupi.yuaicodemother.langgraph4j.state.WorkflowContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 带 Checkpoint 的工作流服务
 *
 * <p>与普通 {@link CodeGenWorkflow} 的区别：
 * <ul>
 *   <li>编译图是 <b>单例</b>，整个应用生命周期内共享同一个 {@link MemorySaver}</li>
 *   <li>每次执行时传入 {@code threadId}（用 appId），图会自动在内存中保存每个节点执行后的状态快照</li>
 *   <li>可通过相同 {@code threadId} 随时查询上次执行到哪一步、结果是什么</li>
 *   <li>再次以相同 {@code threadId} 提交新 prompt，图能感知上次的执行历史</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class CheckpointedWorkflowService {

    /** 整个应用共享同一个编译图实例，MemorySaver 随之存活 */
    private CompiledGraph<MessagesState<String>> compiledGraph;

    @PostConstruct
    public void init() {
        compiledGraph = new CodeGenWorkflow().createWorkflow(new MemorySaver());
        log.info("[Checkpoint] 带持久化的代码生成工作流初始化完成");
    }

    /**
     * 异步启动工作流，立即返回，不阻塞调用方
     *
     * @param appId  应用 ID，作为 threadId 绑定该次会话的图状态
     * @param prompt 用户输入的提示词
     */
    public void startAsync(Long appId, String prompt) {
        String threadId = "app-" + appId;
        log.info("[Checkpoint] 异步启动工作流，threadId={}, prompt={}", threadId, prompt);

        Thread.startVirtualThread(() -> {
            try {
                WorkflowContext context = WorkflowContext.builder()
                        .originalPrompt(prompt)
                        .currentStep("初始化")
                        .build();

                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        .build();

                // stream 会在每个节点完成后将状态写入 MemorySaver
                for (var step : compiledGraph.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context), config)) {
                    WorkflowContext ctx = WorkflowContext.getContext(step.state());
                    if (ctx != null) {
                        log.info("[Checkpoint] threadId={} 节点完成：{}", threadId, ctx.getCurrentStep());
                    }
                }
                log.info("[Checkpoint] threadId={} 工作流执行完成", threadId);
            } catch (Exception e) {
                log.error("[Checkpoint] threadId={} 工作流执行失败：{}", threadId, e.getMessage(), e);
            }
        });
    }

    /**
     * 查询指定 appId 上一次工作流执行后的最新状态快照
     *
     * @param appId 应用 ID
     * @return 最新的 WorkflowContext，若从未执行过则返回 empty
     */
    public Optional<WorkflowContext> getLatestState(Long appId) {
        String threadId = "app-" + appId;
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            return compiledGraph.getState(config)
                    .map(snapshot -> WorkflowContext.getContext(snapshot.state()));
        } catch (Exception e) {
            log.warn("[Checkpoint] 查询状态失败，threadId={}：{}", threadId, e.getMessage());
            return Optional.empty();
        }
    }
}
