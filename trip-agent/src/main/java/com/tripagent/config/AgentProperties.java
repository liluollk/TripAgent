package com.tripagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 配置属性
 * 类型安全的配置，替代散落的 @Value 注解
 */
@ConfigurationProperties(prefix = "trip.agent")
public record AgentProperties(
        PlanningProperties planning,
        ExecutionProperties execution
) {
    public record PlanningProperties(
            String model,
            float temperature
    ) {}

    public record ExecutionProperties(
            String model,
            float temperature
    ) {}
}
