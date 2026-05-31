package com.tripagent.agent.core;

import com.tripagent.utils.McpToolHelper;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry for managing tools available to agents.
 * Wraps McpToolHelper and provides a unified interface for tool calling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final McpToolHelper mcpToolHelper;
    private final ObjectMapper objectMapper;

    /**
     * Tool definition
     */
    public record ToolDefinition(
        String name,
        String description
    ) {}

    /**
     * Registered tools
     */
    private final Map<String, ToolDefinition> tools = new HashMap<>();

    /**
     * Initialize default tools
     */
    public void initDefaultTools() {
        registerTool(new ToolDefinition(
            "getWeather",
            "Get weather information for a city"
        ));

        registerTool(new ToolDefinition(
            "searchAttractions",
            "Search for tourist attractions in a city"
        ));

        registerTool(new ToolDefinition(
            "searchHotels",
            "Search for hotels in a city"
        ));

        registerTool(new ToolDefinition(
            "searchRestaurants",
            "Search for restaurants in a city"
        ));

        log.info("Initialized {} default tools", tools.size());
    }

    /**
     * Register a tool
     */
    public void registerTool(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * Execute a tool by name
     */
    public String executeTool(String toolName, String input) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        log.debug("Executing tool: {} with input: {}", toolName, input);
        try {
            // MCP 工具需要 JSON 格式参数，将纯文本输入包装为 JSON
            String jsonInput = wrapInputToJson(toolName, input);
            log.debug("Tool params (JSON): {}", jsonInput);

            // Use McpToolHelper to call the actual MCP tool
            var result = mcpToolHelper.callTool(toolName, jsonInput);
            String output = result.orElse("{\"error\": \"Tool returned empty result\"}");
            log.debug("Tool result: {}", output);
            return output;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            throw e;
        }
    }

    /**
     * 将纯文本输入包装为 MCP 工具所需的 JSON 格式
     * 所有工具都接受单个 city 参数
     */
    private String wrapInputToJson(String toolName, String input) {
        // 如果已经是有效 JSON，直接返回
        if (input != null && input.trim().startsWith("{")) {
            return input;
        }
        // 使用 ObjectMapper 安全构建 JSON，避免注入问题
        try {
            return objectMapper.writeValueAsString(Map.of("city", input == null ? "" : input));
        } catch (Exception e) {
            log.error("构建 JSON 参数失败", e);
            return "{\"city\": \"\"}";
        }
    }

    /**
     * Get all tool names
     */
    public Set<String> getToolNames() {
        return tools.keySet();
    }

    /**
     * Check if a tool exists
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * Get tool description for prompt
     */
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }
}
