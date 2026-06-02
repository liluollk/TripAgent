package com.tripagent.agent.core;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Context for agent execution.
 * Contains all information needed for an agent to perform its task.
 */
@Data
@Builder
public class AgentContext {

    /**
     * User ID for multi-user support
     */
    private String userId;

    /**
     * Session ID for conversation tracking
     */
    private String sessionId;

    /**
     * User's original message
     */
    private String userMessage;

    /**
     * Current conversation history
     */
    private List<ChatMessage> chatHistory;

    /**
     * Extracted requirements from user
     */
    private Map<String, Object> requirements;

    /**
     * Current plan (for execution phase)
     * 类型安全：使用 {@link #setCurrentPlan(Object)} 和 {@link #getCurrentPlanAs(Class)} 进行访问
     */
    private Object currentPlan;

    /**
     * Current step index in plan
     */
    private Integer currentStepIndex;

    /**
     * Results from previous steps
     */
    private List<Object> stepResults;

    /**
     * 类型安全地获取 currentPlan
     *
     * @param type 期望的 Plan 类型
     * @param <T>  Plan 类型
     * @return 类型转换后的 Plan
     * @throws ClassCastException 如果 currentPlan 不是期望的类型
     * @throws IllegalStateException 如果 currentPlan 为 null
     */
    public <T> T getCurrentPlanAs(Class<T> type) {
        if (currentPlan == null) {
            throw new IllegalStateException("currentPlan is null");
        }
        return type.cast(currentPlan);
    }

    /**
     * Chat message representation
     */
    @Data
    @Builder
    public static class ChatMessage {
        private String role;  // "user" or "assistant"
        private String content;
    }
}
