package com.tripagent.service;

import com.tripagent.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * 记忆提取服务 - 从用户消息中提取城市和旅行偏好
 */
@Slf4j
@Service
public class MemoryExtractionService {

    /**
     * 记忆提取结果
     */
    public record ExtractionResult(String city, String preferences) {}

    /**
     * 无意义消息的常见模式（不值得调用 AI 提取记忆）
     */
    private static final Set<String> TRIVIAL_MESSAGES = Set.of(
            "hi", "hello", "hey", "ok", "okay", "yes", "no", "thanks", "thank you",
            "你好", "您好", "嗯", "好的", "是", "不是", "谢谢", "谢了", "知道了",
            "明白了", "对", "行", "可以", "没问题", "再见", "拜拜", "88", "886"
    );

    /**
     * 支持的城市列表
     */
    private static final String[] SUPPORTED_CITIES = {
            "北京", "上海", "广州", "深圳", "杭州", "南京", "成都", "重庆",
            "西安", "武汉", "苏州", "天津", "郑州", "长沙", "青岛", "大连",
            "厦门", "昆明", "贵阳", "哈尔滨", "沈阳", "济南", "福州", "合肥"
    };

    private final ChatModel planningChatModel;
    private final ObjectMapper objectMapper;

    @Value("${trip.agent.memory.ai-extraction.enabled:true}")
    private boolean aiExtractionEnabled;

    public MemoryExtractionService(
            @Qualifier("planningChatModel") ChatModel planningChatModel,
            ObjectMapper objectMapper) {
        this.planningChatModel = planningChatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 提取记忆（先用规则过滤，再调用 AI）
     */
    public ExtractionResult extract(String userMessage) {
        // 1. 先用规则提取
        ExtractionResult fallback = fallbackExtraction(userMessage);

        // 2. 如果规则提取到结果，或者 AI 提取被禁用，直接返回
        if (!aiExtractionEnabled || fallback.city() != null || fallback.preferences() != null) {
            return fallback;
        }

        // 3. 短消息或无意义消息，跳过 AI 提取（节省 API 费用）
        if (isTrivialMessage(userMessage)) {
            return fallback;
        }

        // 4. 否则调用 AI 提取
        return extractWithAI(userMessage);
    }

    /**
     * 判断是否为无意义的短消息（不需要 AI 提取记忆）
     */
    private boolean isTrivialMessage(String message) {
        if (message == null) return true;

        String trimmed = message.trim();

        // 太短的消息（少于 5 个字符）
        if (trimmed.length() < 5) return true;

        // 常见的寒暄/确认消息
        if (TRIVIAL_MESSAGES.contains(trimmed.toLowerCase())) return true;

        // 纯标点/符号/数字
        if (trimmed.matches("^[\\p{Punct}\\s\\d\\p{So}]+$")) return true;

        return false;
    }

    /**
     * 使用 AI 从用户消息中提取城市和旅行偏好
     */
    private ExtractionResult extractWithAI(String userMessage) {
        String prompt = """
                分析用户消息，提取以下信息：
                1. 用户明确提到的城市名称（只返回城市名，如"杭州"；如果没有提到城市则返回null）
                2. 用户表达的旅行偏好或风格（如：喜欢美食、偏好自然风光、预算敏感、亲子游等；如果没有表达偏好则返回null）

                注意：
                - 只提取用户明确表达的偏好，不要推断
                - 偏好用简短的中文描述，如"美食爱好者""自然风光偏好""经济型旅行"
                - 如果消息只是查天气/查景点等操作性请求，没有表达个人偏好，preferences返回null

                用户消息：%s

                只返回JSON，不要其他内容：
                {"city": "城市名或null", "preferences": "偏好描述或null"}
                """.formatted(userMessage);

        try {
            String response = planningChatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();

            String json = JsonUtils.extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            String city = node.has("city") && !node.get("city").isNull()
                    ? node.get("city").asText() : null;
            String preferences = node.has("preferences") && !node.get("preferences").isNull()
                    ? node.get("preferences").asText() : null;

            log.info("AI提取记忆: city={}, preferences={}", city, preferences);
            return new ExtractionResult(city, preferences);
        } catch (Exception e) {
            log.warn("AI提取记忆失败，降级到关键词匹配: {}", e.getMessage());
            return fallbackExtraction(userMessage);
        }
    }

    /**
     * 降级提取逻辑（关键词匹配）
     */
    private ExtractionResult fallbackExtraction(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        // 偏好提取
        String preferences = null;
        if (lowerMessage.contains("预算") || lowerMessage.contains("便宜") || lowerMessage.contains("省钱")) {
            preferences = "经济型旅行";
        } else if (lowerMessage.contains("豪华") || lowerMessage.contains("高端")) {
            preferences = "豪华型旅行";
        } else if (lowerMessage.contains("休闲") || lowerMessage.contains("度假")) {
            preferences = "休闲度假风格";
        } else if (lowerMessage.contains("探险") || lowerMessage.contains("户外")) {
            preferences = "探险户外风格";
        } else if (lowerMessage.contains("文化") || lowerMessage.contains("历史")) {
            preferences = "文化历史风格";
        }

        // 城市提取
        String city = null;
        for (String c : SUPPORTED_CITIES) {
            if (userMessage.contains(c)) {
                city = c;
                break;
            }
        }

        return new ExtractionResult(city, preferences);
    }
}
