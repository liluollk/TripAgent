package com.tripagent.agent.planning;

import com.tripagent.agent.core.*;
import com.tripagent.agent.core.IntentRecognizer.Intent;
import com.tripagent.knowledge.KnowledgeService;
import com.tripagent.service.TokenUsageTracker.CallType;
import com.tripagent.service.memory.LongTermMemoryService;
import com.tripagent.service.memory.MemoryCompressionService;
import com.tripagent.utils.JsonUtils;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.*;

/**
 * 旅行规划 Agent
 * 使用 Spring AI 原生工具调用 + ReAct 循环生成旅行计划，集成 RAG 知识增强
 */
@Slf4j
@Component
public class PlanningAgent implements Agent {

    private final ChatClient planningChatClient;
    private final ReActLoop reActLoop;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;
    private final LongTermMemoryService longTermMemoryService;
    private final MemoryCompressionService memoryCompressionService;
    private final IntentRecognizer intentRecognizer;

    public PlanningAgent(
            @Qualifier("planningChatModel") ChatModel planningChatModel,
            ToolCallbackProvider toolCallbackProvider,
            ReActLoop reActLoop,
            ObjectMapper objectMapper,
            KnowledgeService knowledgeService,
            LongTermMemoryService longTermMemoryService,
            MemoryCompressionService memoryCompressionService,
            TokenUsageAdvisor tokenUsageAdvisor,
            IntentRecognizer intentRecognizer) {
        // 包装 ToolCallbackProvider 以捕获工具调用事件
        ToolEventCapture toolEventCapture = new ToolEventCapture(toolCallbackProvider);

        this.planningChatClient = ChatClient.builder(planningChatModel)
                .defaultToolCallbacks(toolEventCapture)
                .defaultAdvisors(tokenUsageAdvisor)
                .build();
        this.reActLoop = reActLoop;
        this.objectMapper = objectMapper;
        this.knowledgeService = knowledgeService;
        this.longTermMemoryService = longTermMemoryService;
        this.memoryCompressionService = memoryCompressionService;
        this.intentRecognizer = intentRecognizer;
    }

    private static final String SYSTEM_PROMPT = """
        你是一个旅行规划专家，擅长创建详细的旅行计划。

        当收到旅行请求时，你应该：
        1. 分析用户需求
        2. 调用可用工具收集信息（天气、景点、酒店、餐厅）
        3. 结合本地人攻略知识，创建专业且实用的计划

        可用工具：
        - getWeather: 获取城市天气
        - searchAttractions: 搜索景点
        - searchHotels: 搜索酒店
        - searchRestaurants: 搜索餐厅

        重要规则：
        1. 必须用中文回复
        2. 如果提供了【南京本地人攻略参考】，优先使用攻略中的建议
        3. 先调用工具收集信息，再根据收集到的信息生成计划
        4. 最终回复必须是有效的 JSON 对象，包裹在 ```json ... ``` 标签中
        5. 不要在 JSON 块前后添加任何额外文本

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

        // 先压缩历史消息（异步），然后构建用户消息并执行规划
        // 避免在 Flux 链中调用 block()，防止阻塞 Reactor 线程
        return compressHistoryIfNeeded(context)
                .thenMany(Flux.defer(() -> {
                    // 意图识别：判断是否需要 RAG 检索
                    Intent intent = intentRecognizer.recognize(context.getUserMessage());
                    log.debug("用户意图: {}", intent);

                    String userMessage = buildUserMessage(context, intent);
                    return reActLoop.execute(
                            planningChatClient,
                            SYSTEM_PROMPT,
                            userMessage,
                            context.getChatHistory(),
                            context.getSessionId(),
                            CallType.PLANNING
                    );
                }));
    }

    /**
     * 异步压缩历史消息（如果需要）
     */
    private Mono<Void> compressHistoryIfNeeded(AgentContext context) {
        if (context.getChatHistory() == null || context.getChatHistory().isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            try {
                memoryCompressionService.compressMessages(context.getSessionId(), context.getChatHistory());
            } catch (Exception e) {
                log.warn("历史消息压缩失败，继续规划: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 构建用户消息，根据意图决定是否包含 RAG 知识上下文
     */
    private String buildUserMessage(AgentContext context, Intent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("旅行请求：").append(context.getUserMessage()).append("\n\n");

        if (context.getRequirements() != null && !context.getRequirements().isEmpty()) {
            sb.append("提取的需求：\n");
            context.getRequirements().forEach((key, value) ->
                    sb.append("- ").append(key).append(": ").append(value).append("\n")
            );
        }

        // 添加用户长期记忆上下文
        try {
            String memoryContext = longTermMemoryService.formatMemoriesForPrompt(context.getSessionId());
            if (!memoryContext.isEmpty()) {
                sb.append(memoryContext);
                log.debug("用户长期记忆已添加到规划提示");
            }
        } catch (Exception e) {
            log.warn("获取用户记忆失败，继续规划: {}", e.getMessage());
        }

        // 添加历史摘要上下文
        try {
            String historicalSummary = memoryCompressionService.getHistoricalSummary(context.getSessionId());
            if (!historicalSummary.isEmpty()) {
                sb.append("\n【历史对话摘要】\n").append(historicalSummary).append("\n");
                log.debug("历史摘要已添加到规划提示");
            }
        } catch (Exception e) {
            log.warn("获取历史摘要失败，继续规划: {}", e.getMessage());
        }

        // RAG 知识增强：仅在意图为 RAG 时添加攻略上下文
        if (intent == Intent.RAG) {
            try {
                String ragContext = knowledgeService.getRagContext(context.getUserMessage());
                if (!ragContext.isEmpty()) {
                    sb.append("\n").append(ragContext);
                    log.debug("RAG 上下文已添加到规划提示");
                }
            } catch (Exception e) {
                log.warn("RAG 检索失败，继续规划: {}", e.getMessage());
            }
        } else {
            log.debug("意图非 RAG，跳过知识库检索");
        }

        return sb.toString();
    }

    /**
     * 解析计划
     */
    public Plan parsePlan(String result) {
        try {
            String json = JsonUtils.extractJsonFromAiResponse(result);
            json = normalizeJson(json);
            log.debug("标准化后的 JSON: {}", json);
            return objectMapper.readValue(json, Plan.class);
        } catch (Exception e) {
            log.error("解析计划失败: {}", result, e);
            throw new RuntimeException("解析计划失败", e);
        }
    }

    /**
     * 标准化 JSON，修复 LLM 输出的常见问题
     */
    private String normalizeJson(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)\"type\"\\s*:\\s*\"(attractions?|hotels?|restaurants?|weather|budget)\""
        ).matcher(json);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1).toUpperCase();
            if (value.endsWith("S") && !value.equals("WEATHER") && !value.equals("BUDGET")) {
                value = value.substring(0, value.length() - 1);
            }
            matcher.appendReplacement(sb, "\"type\": \"" + value + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
