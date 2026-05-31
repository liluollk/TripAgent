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
     * Chat message representation
     */
    @Data
    @Builder
    public static class ChatMessage {
        private String role;  // "user" or "assistant"
        private String content;
    }
}
