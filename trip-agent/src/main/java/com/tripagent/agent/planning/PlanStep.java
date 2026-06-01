package com.tripagent.agent.planning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 旅行计划步骤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {

    /**
     * 步骤索引
     */
    private int index;

    /**
     * 步骤类型：天气、景点、酒店、餐厅、预算
     */
    private StepType type;

    /**
     * 城市
     */
    private String city;

    /**
     * 步骤描述
     */
    private String description;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具输入参数
     */
    private String toolInput;

    /**
     * 步骤类型枚举
     */
    public enum StepType {
        WEATHER,
        ATTRACTION,
        HOTEL,
        RESTAURANT,
        BUDGET
    }
}
