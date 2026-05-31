package com.tripagent.utils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * MCP 工具调用助手 - 统一处理 MCP 工具调用和响应解析
 */
@Slf4j
@Component
public class McpToolHelper {

    private final ObjectMapper objectMapper;
    private final ToolCallbackProvider toolCallbackProvider;

    public McpToolHelper(ObjectMapper objectMapper, ToolCallbackProvider toolCallbackProvider) {
        this.objectMapper = objectMapper;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * 根据名称查找工具
     */
    public Optional<ToolCallback> findTool(String toolName) {
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        return Arrays.stream(toolCallbacks)
                .filter(tc -> tc.getToolDefinition().name().equals(toolName))
                .findFirst();
    }

    /**
     * 调用 MCP 工具并返回原始结果
     */
    public Optional<String> callTool(String toolName, String params) {
        Optional<ToolCallback> tool = findTool(toolName);
        if (tool.isEmpty()) {
            log.error("未找到工具: {}", toolName);
            return Optional.empty();
        }

        try {
            log.debug("调用工具: {}", toolName);
            if (log.isTraceEnabled()) {
                log.trace("工具参数: {}", params);
            }
            String result = tool.get().call(params);
            if (log.isTraceEnabled()) {
                log.trace("工具返回: {}", result);
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.error("调用工具失败: {} - {}", toolName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 调用 MCP 工具并解析为指定类型
     */
    public <T> Optional<T> callToolAndParse(String toolName, String params, Class<T> valueType) {
        return callTool(toolName, params)
                .map(this::extractTextFromMcpResponse)
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, valueType));
                    } catch (Exception e) {
                        log.error("解析数据失败: {}", e.getMessage());
                        return Optional.empty();
                    }
                });
    }

    /**
     * 调用 MCP 工具并解析为列表类型
     */
    public <T> List<T> callToolAndParseList(String toolName, String params, TypeReference<List<T>> typeReference) {
        return callTool(toolName, params)
                .map(this::extractTextFromMcpResponse)
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, typeReference));
                    } catch (Exception e) {
                        log.error("解析列表数据失败: {}", e.getMessage());
                        return Optional.empty();
                    }
                })
                .orElse(new ArrayList<>());
    }

    /**
     * 调用城市相关工具（自动构建参数）
     */
    public <T> Optional<T> callCityTool(String toolName, String city, Class<T> valueType) {
        String params = buildCityParams(city);
        return callToolAndParse(toolName, params, valueType);
    }

    /**
     * 调用城市相关工具并返回列表（自动构建参数）
     */
    public <T> List<T> callCityToolAsList(String toolName, String city, TypeReference<List<T>> typeReference) {
        String params = buildCityParams(city);
        return callToolAndParseList(toolName, params, typeReference);
    }

    /**
     * 用 ObjectMapper 安全构建城市参数 JSON
     */
    private String buildCityParams(String city) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("city", city));
        } catch (Exception e) {
            log.error("构建参数失败", e);
            return "{\"city\": \"\"}";
        }
    }

    /**
     * 从 MCP 工具响应中提取 text 字段
     * MCP 工具返回格式: [{"text":"..."}]
     */
    public String extractTextFromMcpResponse(String mcpResponse) {
        try {
            JsonNode arrayNode = objectMapper.readTree(mcpResponse);
            if (arrayNode.isArray() && !arrayNode.isEmpty()) {
                JsonNode firstElement = arrayNode.get(0);
                if (firstElement.has("text")) {
                    return firstElement.get("text").asText();
                }
            }
        } catch (Exception e) {
            log.error("解析 MCP 响应失败: {}", e.getMessage());
        }
        return mcpResponse;
    }
}
