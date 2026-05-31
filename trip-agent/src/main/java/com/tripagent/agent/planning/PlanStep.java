package com.tripagent.agent.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single step in a travel plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {

    /**
     * Step index in the plan
     */
    private int index;

    /**
     * Step type: WEATHER, ATTRACTION, HOTEL, RESTAURANT, BUDGET
     */
    private StepType type;

    /**
     * City for this step
     */
    private String city;

    /**
     * Description of what this step does
     */
    private String description;

    /**
     * Tool to call for this step
     */
    private String toolName;

    /**
     * Input for the tool
     */
    private String toolInput;

    /**
     * Step type enum
     */
    public enum StepType {
        WEATHER,
        ATTRACTION,
        HOTEL,
        RESTAURANT,
        BUDGET
    }
}
