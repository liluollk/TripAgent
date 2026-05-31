package com.tripagent.agent.core;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a single step in agent's reasoning-acting process.
 */
@Data
@Builder
public class AgentStep {

    /**
     * Step type: THINK, ACT, OBSERVE, RESULT, ERROR
     */
    private StepType type;

    /**
     * Content of the step (reasoning, action, observation, etc.)
     */
    private String content;

    /**
     * Tool name if this is an ACT step
     */
    private String toolName;

    /**
     * Tool input if this is an ACT step
     */
    private String toolInput;

    /**
     * Tool output if this is an OBSERVE step
     */
    private String toolOutput;

    /**
     * Step type enum
     */
    public enum StepType {
        THINK,   // Agent is reasoning
        ACT,     // Agent is calling a tool
        OBSERVE, // Agent received tool result
        RESULT,  // Final result
        ERROR    // Error occurred
    }
}
