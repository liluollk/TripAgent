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
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Trip Agent coordinator.
 * Implements Plan-and-Execute pattern with ReAct.
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

        // Phase 1: Planning
        Flux<AgentStep> planningPhase = Flux.concat(
                Flux.just(AgentStep.builder()
                        .type(AgentStep.StepType.THINK)
                        .content("Starting planning phase...")
                        .build()),
                planningAgent.execute(context)
                        .doOnError(e -> log.error("Planning failed", e))
        );

        // Collect planning results, then execute
        return planningPhase.collectList()
                .flatMapMany(planningSteps -> {
                    // Extract plan result
                    String planResult = planningSteps.stream()
                            .filter(s -> s.getType() == AgentStep.StepType.RESULT)
                            .map(AgentStep::getContent)
                            .findFirst()
                            .orElse("");

                    if (planResult.isEmpty()) {
                        return Flux.just(buildErrorStep("No plan generated"));
                    }

                    // Parse plan
                    Plan plan;
                    try {
                        plan = planningAgent.parsePlan(planResult);
                    } catch (Exception e) {
                        log.error("Failed to parse plan", e);
                        return Flux.just(buildErrorStep("Failed to parse plan: " + e.getMessage()));
                    }

                    // Send planning SSE event
                    sseEventEmitter.sendPlanning(context.getSessionId(), plan);

                    // Emit all planning steps
                    Flux<AgentStep> planningOutput = Flux.fromIterable(planningSteps);

                    // Phase 2: Execute steps sequentially
                    Flux<AgentStep> executionPhase = executeStepsSequentially(context, plan);

                    return Flux.concat(planningOutput, executionPhase);
                })
                .onErrorResume(e -> {
                    log.error("TripAgent execution failed", e);
                    return Flux.just(buildErrorStep("Execution failed: " + e.getMessage()));
                });
    }

    /**
     * Execute all plan steps sequentially, collecting results
     */
    private Flux<AgentStep> executeStepsSequentially(AgentContext context, Plan plan) {
        List<StepResult> stepResults = new ArrayList<>();

        Flux<AgentStep> startMsg = Flux.just(AgentStep.builder()
                .type(AgentStep.StepType.THINK)
                .content("Plan generated. Starting execution...")
                .build());

        // Use concatMap to execute steps one by one in order
        Flux<AgentStep> steps = Flux.range(0, plan.getTotalSteps())
                .concatMap(i -> executeSingleStep(context, plan, i, stepResults));

        return Flux.concat(startMsg, steps);
    }

    /**
     * Execute a single plan step and return its AgentStep stream
     */
    private Flux<AgentStep> executeSingleStep(
            AgentContext context, Plan plan, int stepIndex, List<StepResult> stepResults) {

        PlanStep step = plan.getStep(stepIndex);
        log.info("Executing step {}/{}: {}", stepIndex + 1, plan.getTotalSteps(), step.getDescription());

        // Send executing SSE event
        sseEventEmitter.sendExecuting(context.getSessionId(), step.getDescription(), "executing");

        // Build context for execution
        AgentContext execContext = AgentContext.builder()
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .currentPlan(plan)
                .currentStepIndex(stepIndex)
                .build();

        // Execute and collect steps, sending ReAct events
        return executionAgent.execute(execContext)
                .doOnNext(agentStep -> {
                    // 发送 ReAct 循环的每个步骤到前端
                    switch (agentStep.getType()) {
                        case THINK -> sseEventEmitter.sendThinking(context.getSessionId(), agentStep.getContent());
                        case ACT -> sseEventEmitter.sendToolCall(context.getSessionId(), agentStep.getToolName(), agentStep.getToolInput());
                        case OBSERVE -> sseEventEmitter.sendToolResult(context.getSessionId(), agentStep.getToolOutput());
                        default -> {}
                    }
                })
                .collectList()
                .flatMapMany(execSteps -> {
                    // Extract result
                    String result = extractResult(execSteps);

                    StepResult stepResult = executionAgent.parseStepResult(
                            step, result, execSteps.size());
                    stepResults.add(stepResult);

                    // Send completed SSE event
                    sseEventEmitter.sendExecuting(
                            context.getSessionId(), step.getDescription(), "completed");

                    // If all steps done, generate final result
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
                    log.error("Step {} execution failed", stepIndex, e);
                    sseEventEmitter.sendExecuting(
                            context.getSessionId(), step.getDescription(), "failed");

                    // Record failure and continue
                    StepResult failedResult = StepResult.failure(
                            stepIndex, step.getType().name(), step.getCity(), e.getMessage(), 0);
                    stepResults.add(failedResult);

                    // If all steps done (including failures), generate final result
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
                            .content("Step failed: " + e.getMessage())
                            .build());
                });
    }

    /**
     * Extract result text from agent steps
     */
    private String extractResult(List<AgentStep> steps) {
        return steps.stream()
                .filter(s -> s.getType() == AgentStep.StepType.RESULT)
                .map(AgentStep::getContent)
                .findFirst()
                .orElse("");
    }

    /**
     * Build an error step
     */
    private AgentStep buildErrorStep(String message) {
        return AgentStep.builder()
                .type(AgentStep.StepType.ERROR)
                .content(message)
                .build();
    }

    /**
     * Generate final result from plan and step results
     */
    private String generateFinalResult(Plan plan, List<StepResult> stepResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("Travel Plan Summary\n");
        sb.append("==================\n\n");

        sb.append("Cities: ").append(String.join(", ", plan.getCities())).append("\n");
        sb.append("Total Steps: ").append(plan.getTotalSteps()).append("\n\n");

        sb.append("Step Results:\n");
        for (StepResult result : stepResults) {
            sb.append(String.format("- Step %d (%s in %s): %s\n",
                    result.getStepIndex() + 1,
                    result.getStepType(),
                    result.getCity(),
                    result.isSuccess() ? "Success" : "Failed: " + result.getErrorMessage()));
        }

        return sb.toString();
    }
}
