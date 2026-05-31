package com.tripagent.agent.planning;

import com.tripagent.agent.core.*;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.*;

/**
 * Planning Agent with full ReAct loop.
 * Generates travel plans by reasoning and using tools.
 */
@Slf4j
@Component
public class PlanningAgent implements Agent {

    private final ChatModel planningChatModel;
    private final ReActLoop reActLoop;
    private final ObjectMapper objectMapper;

    public PlanningAgent(
            @Qualifier("planningChatModel") ChatModel planningChatModel,
            ReActLoop reActLoop,
            ObjectMapper objectMapper) {
        this.planningChatModel = planningChatModel;
        this.reActLoop = reActLoop;
        this.objectMapper = objectMapper;
    }

    private static final String SYSTEM_PROMPT = """
        You are a travel planning expert. Your job is to create detailed travel plans.

        When given a travel request, you should:
        1. Think about what information you need
        2. Use tools to gather information (weather, attractions, hotels, restaurants)
        3. Create a comprehensive plan

        To use a tool, respond with:
        ```tool
        toolName:input
        ```

        Available tools:
        - getWeather: Get weather for a city (input: city name)
        - searchAttractions: Search attractions in a city (input: city name)
        - searchHotels: Search hotels in a city (input: city name)
        - searchRestaurants: Search restaurants in a city (input: city name)

        IMPORTANT RULES:
        1. Always respond in Chinese.
        2. Your FINAL response MUST be a valid JSON object, wrapped in ```json ... ``` tags.
        3. Do NOT add any text before or after the JSON block.

        Final JSON format:
        ```json
        {
            "cities": ["city1", "city2"],
            "steps": [
                {
                    "index": 0,
                    "type": "WEATHER",
                    "city": "city1",
                    "description": "获取天气信息",
                    "toolName": "getWeather",
                    "toolInput": "city1"
                }
            ],
            "summary": "行程概要",
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
     * Build user message from context
     */
    private String buildUserMessage(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Travel request: ").append(context.getUserMessage()).append("\n\n");

        if (context.getRequirements() != null && !context.getRequirements().isEmpty()) {
            sb.append("Extracted requirements:\n");
            context.getRequirements().forEach((key, value) ->
                    sb.append("- ").append(key).append(": ").append(value).append("\n")
            );
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
