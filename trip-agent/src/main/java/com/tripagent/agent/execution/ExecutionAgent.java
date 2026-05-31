package com.tripagent.agent.execution;

import com.tripagent.agent.core.*;
import com.tripagent.agent.planning.Plan;
import com.tripagent.agent.planning.PlanStep;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.*;

/**
 * Execution Agent with limited ReAct loop.
 * Executes plan steps with max 3 iterations per step.
 */
@Slf4j
@Component
public class ExecutionAgent implements Agent {

    private final ChatModel executionChatModel;
    private final ReActLoop reActLoop;
    private final ObjectMapper objectMapper;

    public ExecutionAgent(
            @Qualifier("executionChatModel") ChatModel executionChatModel,
            ReActLoop reActLoop,
            ObjectMapper objectMapper) {
        this.executionChatModel = executionChatModel;
        this.reActLoop = reActLoop;
        this.objectMapper = objectMapper;
    }

    /**
     * Maximum ReAct iterations per step
     */
    private static final int MAX_ITERATIONS_PER_STEP = 3;

    private static final String SYSTEM_PROMPT = """
        You are a travel information executor. Your job is to execute a single plan step.

        You will receive a step description and should:
        1. Think about what tool to use
        2. Call the appropriate tool
        3. Return the result

        To use a tool, respond with:
        ```tool
        toolName:input
        ```

        Available tools:
        - getWeather: Get weather for a city (input: city name)
        - searchAttractions: Search attractions in a city (input: city name)
        - searchHotels: Search hotels in a city (input: city name)
        - searchRestaurants: Search restaurants in a city (input: city name)

        Return the tool result directly. Do not add extra explanation.
        """;

    @Override
    public String getName() {
        return "ExecutionAgent";
    }

    @Override
    public Flux<AgentStep> execute(AgentContext context) {
        log.info("ExecutionAgent executing for session: {}", context.getSessionId());

        // Get current plan and step
        Plan plan = (Plan) context.getCurrentPlan();
        int stepIndex = context.getCurrentStepIndex();
        PlanStep step = plan.getStep(stepIndex);

        if (step == null) {
            return Flux.just(AgentStep.builder()
                    .type(AgentStep.StepType.ERROR)
                    .content("Step not found at index: " + stepIndex)
                    .build());
        }

        // Build user message for this step
        String userMessage = buildStepMessage(step);

        // Execute with limited ReAct loop
        return reActLoop.execute(
                executionChatModel,
                SYSTEM_PROMPT,
                userMessage,
                null  // No chat history for execution
        ).take(MAX_ITERATIONS_PER_STEP * 2);  // Limit iterations (each iteration has THINK + ACT/OBSERVE)
    }

    /**
     * Build message for a single step
     */
    private String buildStepMessage(PlanStep step) {
        return String.format(
                "Execute step %d: %s\nCity: %s\nDescription: %s\nTool: %s\nInput: %s",
                step.getIndex(),
                step.getType(),
                step.getCity(),
                step.getDescription(),
                step.getToolName(),
                step.getToolInput()
        );
    }

    /**
     * Parse step result from agent output
     */
    public StepResult parseStepResult(PlanStep step, String result, int iterations) {
        try {
            // Try to parse as JSON
            Object data = objectMapper.readValue(result, Object.class);
            return StepResult.success(
                    step.getIndex(),
                    step.getType().name(),
                    step.getCity(),
                    data,
                    iterations
            );
        } catch (Exception e) {
            // Return as plain text
            return StepResult.success(
                    step.getIndex(),
                    step.getType().name(),
                    step.getCity(),
                    result,
                    iterations
            );
        }
    }
}
