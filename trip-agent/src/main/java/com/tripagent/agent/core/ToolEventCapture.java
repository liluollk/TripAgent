package com.tripagent.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工具事件捕获器
 * 包装 ToolCallbackProvider，在每次工具调用前后记录事件，用于 SSE 推送。
 * 通过 ThreadLocal 传递 sessionId 和事件收集器。
 */
@Slf4j
public class ToolEventCapture implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;

    /**
     * 当前会话的 sessionId
     */
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    /**
     * 当前会话收集到的工具事件
     */
    private static final ThreadLocal<List<AgentStep>> TOOL_EVENTS = ThreadLocal.withInitial(ArrayList::new);

    public ToolEventCapture(ToolCallbackProvider delegate) {
        this.delegate = delegate;
    }

    public static void setSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    public static String getSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    /**
     * 获取并清除当前会话的工具事件
     */
    public static List<AgentStep> collectEvents() {
        List<AgentStep> events = TOOL_EVENTS.get();
        List<AgentStep> result = new ArrayList<>(events);
        events.clear();
        return result;
    }

    public static void cleanup() {
        CURRENT_SESSION_ID.remove();
        TOOL_EVENTS.remove();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        ToolCallback[] original = delegate.getToolCallbacks();
        return Arrays.stream(original)
                .map(this::wrapCallback)
                .toArray(ToolCallback[]::new);
    }

    /**
     * 包装 ToolCallback，在调用前后发射事件
     */
    private ToolCallback wrapCallback(ToolCallback original) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return original.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                String toolName = getToolDefinition().name();

                // 发射 ACT 事件
                TOOL_EVENTS.get().add(AgentStep.builder()
                        .type(AgentStep.StepType.ACT)
                        .toolName(toolName)
                        .toolInput(toolInput)
                        .build());

                log.debug("工具调用: {}({})", toolName, toolInput);

                try {
                    String result = original.call(toolInput);

                    // 发射 OBSERVE 事件
                    TOOL_EVENTS.get().add(AgentStep.builder()
                            .type(AgentStep.StepType.OBSERVE)
                            .toolOutput(result)
                            .build());

                    log.debug("工具结果: {} → {}字符", toolName,
                            result != null ? result.length() : 0);
                    return result;
                } catch (Exception e) {
                    log.error("工具调用失败: {} - {}", toolName, e.getMessage());
                    throw e;
                }
            }
        };
    }
}
