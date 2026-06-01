package com.tripagent.agent;

import com.tripagent.agent.core.*;
import com.tripagent.agent.execution.ExecutionAgent;
import com.tripagent.agent.execution.StepResult;
import com.tripagent.agent.planning.Plan;
import com.tripagent.agent.planning.PlanningAgent;
import com.tripagent.agent.planning.PlanStep;
import com.tripagent.service.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.ArrayList;
import java.util.List;

/**
 * 旅行代理协调器
 * 实现 Plan-and-Execute 模式与 ReAct 循环
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripAgent implements Agent {

    private final PlanningAgent planningAgent;
    private final ExecutionAgent executionAgent;
    private final SessionManager sessionManager;
    private final SseEventEmitter sseEventEmitter;

    @Override
    public String getName() {
        return "TripAgent";
    }

    @Override
    public Flux<AgentStep> execute(AgentContext context) {
        log.info("TripAgent executing for session: {}", context.getSessionId());

        // 阶段 1: 规划
        Flux<AgentStep> planningPhase = Flux.concat(
                Flux.just(AgentStep.builder()
                        .type(AgentStep.StepType.THINK)
                        .content("开始规划阶段...")
                        .build()),
                planningAgent.execute(context)
                        .doOnError(e -> log.error("规划失败", e))
        );

        // 收集规划结果，然后执行
        return planningPhase.collectList()
                .flatMapMany(planningSteps -> {
                    // 提取规划结果
                    String planResult = planningSteps.stream()
                            .filter(s -> s.getType() == AgentStep.StepType.RESULT)
                            .map(AgentStep::getContent)
                            .findFirst()
                            .orElse("");

                    if (planResult.isEmpty()) {
                        return Flux.just(buildErrorStep("未生成计划"));
                    }

                    // 解析计划
                    Plan plan;
                    try {
                        plan = planningAgent.parsePlan(planResult);
                    } catch (Exception e) {
                        log.error("解析计划失败", e);
                        return Flux.just(buildErrorStep("解析计划失败: " + e.getMessage()));
                    }

                    // 发送规划 SSE 事件
                    sseEventEmitter.sendPlanning(context.getSessionId(), plan);

                    // 发送所有规划步骤
                    Flux<AgentStep> planningOutput = Flux.fromIterable(planningSteps);

                    // 阶段 2: 顺序执行步骤
                    Flux<AgentStep> executionPhase = executeStepsSequentially(context, plan);

                    return Flux.concat(planningOutput, executionPhase);
                })
                .onErrorResume(e -> {
                    log.error("TripAgent 执行失败", e);
                    return Flux.just(buildErrorStep("执行失败: " + e.getMessage()));
                });
    }

    /**
     * 顺序执行所有计划步骤，收集结果
     */
    private Flux<AgentStep> executeStepsSequentially(AgentContext context, Plan plan) {
        List<StepResult> stepResults = new ArrayList<>();

        Flux<AgentStep> startMsg = Flux.just(AgentStep.builder()
                .type(AgentStep.StepType.THINK)
                .content("计划已生成，开始执行...")
                .build());

        // 使用 concatMap 按顺序逐步执行
        Flux<AgentStep> steps = Flux.range(0, plan.getTotalSteps())
                .concatMap(i -> executeSingleStep(context, plan, i, stepResults));

        return Flux.concat(startMsg, steps);
    }

    /**
     * 执行单个计划步骤，返回 AgentStep 流
     */
    private Flux<AgentStep> executeSingleStep(
            AgentContext context, Plan plan, int stepIndex, List<StepResult> stepResults) {

        PlanStep step = plan.getStep(stepIndex);
        log.info("执行步骤 {}/{}: {}", stepIndex + 1, plan.getTotalSteps(), step.getDescription());

        // 发送执行中 SSE 事件
        sseEventEmitter.sendExecuting(context.getSessionId(), step.getDescription(), "executing");

        // 构建执行上下文
        AgentContext execContext = AgentContext.builder()
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .currentPlan(plan)
                .currentStepIndex(stepIndex)
                .build();

        // 执行并收集步骤，发送 ReAct 事件
        return executionAgent.execute(execContext)
                .doOnNext(agentStep -> {
                    switch (agentStep.getType()) {
                        case THINK -> sseEventEmitter.sendThinking(context.getSessionId(), agentStep.getContent());
                        case ACT -> sseEventEmitter.sendToolCall(context.getSessionId(), agentStep.getToolName(), agentStep.getToolInput());
                        case OBSERVE -> sseEventEmitter.sendToolResult(context.getSessionId(), agentStep.getToolOutput());
                        default -> {}
                    }
                })
                .collectList()
                .flatMapMany(execSteps -> {
                    // 提取结果
                    String result = extractResult(execSteps);

                    StepResult stepResult = executionAgent.parseStepResult(
                            step, result, execSteps.size());
                    stepResults.add(stepResult);

                    // 发送完成 SSE 事件
                    sseEventEmitter.sendExecuting(
                            context.getSessionId(), step.getDescription(), "completed");

                    // 如果所有步骤完成，生成最终结果
                    if (stepResults.size() == plan.getTotalSteps()) {
                        String finalResult = generateFinalResult(plan, stepResults);
                        sseEventEmitter.sendResult(context.getSessionId(), finalResult);

                        return Flux.concat(
                                Flux.fromIterable(execSteps),
                                Flux.just(AgentStep.builder()
                                        .type(AgentStep.StepType.RESULT)
                                        .content(finalResult)
                                        .build())
                        );
                    }

                    return Flux.fromIterable(execSteps);
                })
                .onErrorResume(e -> {
                    log.error("步骤 {} 执行失败", stepIndex, e);
                    sseEventEmitter.sendExecuting(
                            context.getSessionId(), step.getDescription(), "failed");

                    // 记录失败并继续
                    StepResult failedResult = StepResult.failure(
                            stepIndex, step.getType().name(), step.getCity(), e.getMessage(), 0);
                    stepResults.add(failedResult);

                    // 如果所有步骤完成（包括失败的），生成最终结果
                    if (stepResults.size() == plan.getTotalSteps()) {
                        String finalResult = generateFinalResult(plan, stepResults);
                        sseEventEmitter.sendResult(context.getSessionId(), finalResult);

                        return Flux.just(AgentStep.builder()
                                .type(AgentStep.StepType.RESULT)
                                .content(finalResult)
                                .build());
                    }

                    return Flux.just(AgentStep.builder()
                            .type(AgentStep.StepType.ERROR)
                            .content("步骤失败: " + e.getMessage())
                            .build());
                });
    }

    /**
     * 从代理步骤中提取结果文本
     */
    private String extractResult(List<AgentStep> steps) {
        return steps.stream()
                .filter(s -> s.getType() == AgentStep.StepType.RESULT)
                .map(AgentStep::getContent)
                .findFirst()
                .orElse("");
    }

    /**
     * 构建错误步骤
     */
    private AgentStep buildErrorStep(String message) {
        return AgentStep.builder()
                .type(AgentStep.StepType.ERROR)
                .content(message)
                .build();
    }

    /**
     * 从计划和步骤结果生成最终结果
     */
    private String generateFinalResult(Plan plan, List<StepResult> stepResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("旅行计划摘要\n");
        sb.append("==================\n\n");

        sb.append("城市: ").append(String.join(", ", plan.getCities())).append("\n");
        sb.append("总步骤: ").append(plan.getTotalSteps()).append("\n\n");

        sb.append("步骤结果:\n");
        for (StepResult result : stepResults) {
            sb.append(String.format("- 步骤 %d (%s 在 %s): %s\n",
                    result.getStepIndex() + 1,
                    result.getStepType(),
                    result.getCity(),
                    result.isSuccess() ? "成功" : "失败: " + result.getErrorMessage()));
        }

        return sb.toString();
    }
}
