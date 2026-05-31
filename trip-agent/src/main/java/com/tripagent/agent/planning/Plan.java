package com.tripagent.agent.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Represents a complete travel plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    /**
     * Plan ID
     */
    private String planId;

    /**
     * Cities to visit
     */
    private List<String> cities;

    /**
     * Plan steps
     */
    private List<PlanStep> steps;

    /**
     * Plan summary
     */
    private String summary;

    /**
     * Estimated budget
     */
    private Double estimatedBudget;

    /**
     * Get step by index
     */
    public PlanStep getStep(int index) {
        return steps.stream()
                .filter(s -> s.getIndex() == index)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get total number of steps
     */
    public int getTotalSteps() {
        return steps.size();
    }
}
