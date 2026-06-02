package com.tripagent.agent.execution;

import com.tripagent.agent.core.*;
import com.tripagent.agent.planning.Plan;
import com.tripagent.service.TokenUsageTracker.CallType;
import com.tripagent.agent.planning.PlanStep;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 执行代理，使用 Spring AI 原生工具调用执行单个计划步骤。
 */
@Slf4j
@Component
public class ExecutionAgent implements Agent {

    private final ChatClient executionChatClient;
    private final ReActLoop reActLoop;
    private final ObjectMapper objectMapper;

    public ExecutionAgent(
            @Qualifier("executionChatModel") ChatModel executionChatModel,
            ToolCallbackProvider toolCallbackProvider,
            ReActLoop reActLoop,
            ObjectMapper objectMapper,
            TokenUsageAdvisor tokenUsageAdvisor) {
        // 包装 ToolCallbackProvider 以捕获工具调用事件
        ToolEventCapture toolEventCapture = new ToolEventCapture(toolCallbackProvider);

        this.executionChatClient = ChatClient.builder(executionChatModel)
                .defaultToolCallbacks(toolEventCapture)
                .defaultAdvisors(tokenUsageAdvisor)
                .build();
        this.reActLoop = reActLoop;
        this.objectMapper = objectMapper;
    }

    private static final String SYSTEM_PROMPT = """
        你是旅行信息执行器，负责执行单个计划步骤。

        你会收到一个步骤描述，请直接调用对应的工具获取信息，然后返回结果。

        可用工具：
        - getWeather: 获取城市天气
        - searchAttractions: 搜索景点
        - searchHotels: 搜索酒店
        - searchRestaurants: 搜索餐厅

        直接返回工具结果，不要添加额外解释。
        """;

    @Override
    public String getName() {
        return "ExecutionAgent";
    }

    @Override
    public Flux<AgentStep> execute(AgentContext context) {
        log.info("ExecutionAgent executing for session: {}", context.getSessionId());

        // 获取当前计划和步骤
        Plan plan = context.getCurrentPlanAs(Plan.class);
        int stepIndex = context.getCurrentStepIndex();
        PlanStep step = plan.getStep(stepIndex);

        if (step == null) {
            return Flux.just(AgentStep.builder()
                    .type(AgentStep.StepType.ERROR)
                    .content("Step not found at index: " + stepIndex)
                    .build());
        }

        // 构建步骤执行消息
        String userMessage = buildStepMessage(step);

        // 使用 ReAct 循环执行（Spring AI 自动处理工具调用）
        return reActLoop.execute(
                executionChatClient,
                SYSTEM_PROMPT,
                userMessage,
                context.getChatHistory(),
                context.getSessionId(),
                CallType.EXECUTION
        );
    }

    /**
     * 构建步骤执行消息
     */
    private String buildStepMessage(PlanStep step) {
        return String.format(
                "请执行以下旅行计划步骤：\n" +
                "步骤 %d: %s\n" +
                "城市: %s\n" +
                "说明: %s\n" +
                "需要调用的工具: %s\n" +
                "工具输入: %s",
                step.getIndex(),
                step.getType(),
                step.getCity(),
                step.getDescription(),
                step.getToolName(),
                step.getToolInput()
        );
    }

    /**
     * 从代理输出解析步骤结果
     */
    public StepResult parseStepResult(PlanStep step, String result, int iterations) {
        try {
            // 尝试解析为 JSON
            Object data = objectMapper.readValue(result, Object.class);
            return StepResult.success(
                    step.getIndex(),
                    step.getType().name(),
                    step.getCity(),
                    data,
                    iterations
            );
        } catch (Exception e) {
            // 作为纯文本返回
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
