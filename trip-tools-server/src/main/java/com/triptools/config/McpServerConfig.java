package com.triptools.config;

import com.triptools.tools.TripTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置类
 * 将 TripTools 注册为 MCP 工具提供者
 */
@Configuration
public class McpServerConfig {

    /**
     * 注册 TripTools 为 MCP 工具提供者
     * 使用 @Tool 注解的方法会被自动检测和注册
     */
    @Bean
    public ToolCallbackProvider tripToolsProvider(TripTools tripTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tripTools)
                .build();
    }
}
