package com.tripagent.agent.planning;

import com.tripagent.agent.core.*;
import com.tripagent.knowledge.KnowledgeService;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.*;

/**
 * 旅行规划 Agent
 * 使用 ReAct 循环生成旅行计划，集成 RAG 知识增强
 */
@Slf4j
@Component
public class PlanningAgent implements Agent {

    private final ChatModel planningChatModel;
    private final ReActLoop reActLoop;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;

    public PlanningAgent(
            @Qualifier("planningChatModel") ChatModel planningChatModel,
            ReActLoop reActLoop,
            ObjectMapper objectMapper,
            KnowledgeService knowledgeService) {
        this.planningChatModel = planningChatModel;
        this.reActLoop = reActLoop;
        this.objectMapper = objectMapper;
        this.knowledgeService = knowledgeService;
    }

    private static final String SYSTEM_PROMPT = """
        你是一个旅行规划专家，擅长创建详细的旅行计划。

        当收到旅行请求时，你应该：
        1. 分析用户需求
        2. 使用工具收集信息（天气、景点、酒店、餐厅）
        3. 结合本地人攻略知识，创建专业且实用的计划

        使用工具时，回复格式：
        ```tool
        工具名:输入
        ```

        可用工具：
        - getWeather: 获取城市天气（输入：城市名）
        - searchAttractions: 搜索景点（输入：城市名）
        - searchHotels: 搜索酒店（输入：城市名）
        - searchRestaurants: 搜索餐厅（输入：城市名）

        重要规则：
        1. 必须用中文回复
        2. 如果提供了【南京本地人攻略参考】，优先使用攻略中的建议（如本地人推荐的餐厅、避坑提示等）
        3. 最终回复必须是有效的 JSON 对象，包裹在 ```json ... ``` 标签中
        4. 不要在 JSON 块前后添加任何文本

        最终 JSON 格式：
        ```json
        {
            "cities": ["城市1", "城市2"],
            "steps": [
                {
                    "index": 0,
                    "type": "WEATHER",
                    "city": "城市1",
                    "description": "获取天气信息",
                    "toolName": "getWeather",
                    "toolInput": "城市1"
                }
            ],
            "summary": "行程概要（包含本地人建议）",
            "estimatedBudget": 5000.0
        }
        ```
        """;

    @Override
    public String getName() {
        return "PlanningAgent";
    }

    @Override
    public Flux<AgentStep> execute(AgentContext context) {
        log.info("PlanningAgent executing for session: {}", context.getSessionId());

        // Build user message with requirements
        String userMessage = buildUserMessage(context);

        // Execute ReAct loop
        return reActLoop.execute(
                planningChatModel,
                SYSTEM_PROMPT,
                userMessage,
                context.getChatHistory()
        );
    }

    /**
     * 构建用户消息，包含 RAG 知识上下文
     */
    private String buildUserMessage(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("旅行请求：").append(context.getUserMessage()).append("\n\n");

        if (context.getRequirements() != null && !context.getRequirements().isEmpty()) {
            sb.append("提取的需求：\n");
            context.getRequirements().forEach((key, value) ->
                    sb.append("- ").append(key).append(": ").append(value).append("\n")
            );
        }

        // RAG 知识增强：添加本地人攻略上下文
        try {
            String ragContext = knowledgeService.getRagContext(context.getUserMessage());
            if (!ragContext.isEmpty()) {
                sb.append("\n").append(ragContext);
                log.debug("RAG 上下文已添加到规划提示");
            }
        } catch (Exception e) {
            log.warn("RAG 检索失败，继续规划: {}", e.getMessage());
        }

        return sb.toString();
    }

    /**
     * Parse plan from agent result
     */
    public Plan parsePlan(String result) {
        try {
            // Extract JSON from result
            String json = extractJson(result);
            // Normalize common LLM errors in JSON
            json = normalizeJson(json);
            log.debug("Normalized JSON: {}", json);
            return objectMapper.readValue(json, Plan.class);
        } catch (Exception e) {
            log.error("Failed to parse plan from result: {}", result, e);
            throw new RuntimeException("Failed to parse plan", e);
        }
    }

    /**
     * Normalize JSON to fix common LLM output issues.
     * Uses case-insensitive regex to handle singular/plural/mixed-case enum variations.
     */
    private String normalizeJson(String json) {
        // Fix "type" field enum values: handle plural forms and case variations
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)\"type\"\\s*:\\s*\"(attractions?|hotels?|restaurants?|weather|budget)\""
        ).matcher(json);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1).toUpperCase();
            // Remove trailing 'S' if present (ATTRACTIONS -> ATTRACTION, etc.)
            if (value.endsWith("S") && !value.equals("WEATHER") && !value.equals("BUDGET")) {
                value = value.substring(0, value.length() - 1);
            }
            matcher.appendReplacement(sb, "\"type\": \"" + value + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Extract JSON from text
     */
    private String extractJson(String text) {
        log.debug("Extracting JSON from text (length={}): {}", text.length(),
                text.length() > 500 ? text.substring(0, 500) + "..." : text);

        // Try to find ```json ... ``` block first
        int jsonBlockStart = text.indexOf("```json");
        if (jsonBlockStart >= 0) {
            int contentStart = text.indexOf("\n", jsonBlockStart) + 1;
            int contentEnd = text.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                String json = text.substring(contentStart, contentEnd).trim();
                log.debug("Found JSON in ```json block: {}", json);
                return json;
            }
        }

        // Try ``` ... ``` block without json tag
        int blockStart = text.indexOf("```");
        if (blockStart >= 0) {
            int contentStart = text.indexOf("\n", blockStart) + 1;
            int contentEnd = text.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                String candidate = text.substring(contentStart, contentEnd).trim();
                if (candidate.startsWith("{")) {
                    log.debug("Found JSON in ``` block: {}", candidate);
                    return candidate;
                }
            }
        }

        // Fallback: find first { to last }
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            log.debug("Found JSON by brace matching: {}", json);
            return json;
        }

        log.error("No JSON found in text: {}", text);
        throw new RuntimeException("No JSON found in text");
    }
}
