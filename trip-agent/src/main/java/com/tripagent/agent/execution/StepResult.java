package com.tripagent.agent.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of executing a single plan step.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {

    /**
     * Step index
     */
    private int stepIndex;

    /**
     * Step type
     */
    private String stepType;

    /**
     * City
     */
    private String city;

    /**
     * Whether execution was successful
     */
    private boolean success;

    /**
     * Result data (weather info, attractions, hotels, etc.)
     */
    private Object data;

    /**
     * Error message if failed
     */
    private String errorMessage;

    /**
     * Number of ReAct iterations used
     */
    private int iterationsUsed;

    /**
     * Create a success result
     */
    public static StepResult success(int stepIndex, String stepType, String city, Object data, int iterations) {
        return StepResult.builder()
                .stepIndex(stepIndex)
                .stepType(stepType)
                .city(city)
                .success(true)
                .data(data)
                .iterationsUsed(iterations)
                .build();
    }

    /**
     * Create a failure result
     */
    public static StepResult failure(int stepIndex, String stepType, String city, String error, int iterations) {
        return StepResult.builder()
                .stepIndex(stepIndex)
                .stepType(stepType)
                .city(city)
                .success(false)
                .errorMessage(error)
                .iterationsUsed(iterations)
                .build();
    }
}
