package com.tripagent.agent.core;

import com.tripagent.service.TokenUsageTracker.CallType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 意图识别器
 * <p>
 * 在 Agent 处理用户输入之前，先通过 LLM 轻量级分类判断用户意图，
 * 避免每次对话都触发 RAG 检索，节省不必要的向量检索开销。
 * <p>
 * 当前支持两种意图：
 * <ul>
 *   <li>RAG — 用户在问攻略/景点/美食/交通等知识库相关内容</li>
 *   <li>GENERAL — 闲聊、天气查询、通用对话</li>
 * </ul>
 */
@Slf4j
@Component
public class IntentRecognizer {

    private final ChatClient chatClient;

    private static final String INTENT_PROMPT_TEMPLATE = """
            你是一个意图分类器。请根据用户的输入，判断该问题是否需要从旅行知识库中检索信息来回答。

            知识库中包含的内容主要是：景点推荐、美食攻略、交通指南、避坑建议、最佳旅行时间等旅行相关信息。

            分类规则：
            - 如果用户的问题涉及旅行攻略、景点、美食、交通、住宿、避坑等旅行知识，输出：RAG
            - 如果用户的问题是闲聊、问候、天气查询、行程规划、通用对话等与旅行知识库无关的内容，输出：GENERAL

            示例：
            用户：南京有什么好吃的？ → RAG
            用户：去南京玩几天合适？ → RAG
            用户：南京的交通怎么走？ → RAG
            用户：帮我规划一个三天的行程 → GENERAL
            用户：今天天气怎么样？ → GENERAL
            用户：你好 → GENERAL
            用户：预算5000够吗 → GENERAL

            请只输出一个单词：RAG 或 GENERAL，不要输出任何其他内容。

            用户输入：%s
            """;

    public IntentRecognizer(@Qualifier("planningChatModel") ChatModel chatModel,
                            TokenUsageAdvisor tokenUsageAdvisor) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(tokenUsageAdvisor)
                .build();
    }

    /**
     * 识别用户输入的意图
     *
     * @param userInput 用户输入文本
     * @return 识别出的意图
     */
    public Intent recognize(String userInput) {
        try {
            TokenUsageAdvisor.setCurrentCallType(CallType.PLANNING);
            String prompt = String.format(INTENT_PROMPT_TEMPLATE, userInput);
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (result != null && result.trim().toUpperCase().contains("RAG")) {
                log.debug("意图识别: RAG — {}", userInput);
                return Intent.RAG;
            }

            log.debug("意图识别: GENERAL — {}", userInput);
            return Intent.GENERAL;
        } catch (Exception e) {
            log.warn("意图识别失败，降级为 GENERAL: {}", e.getMessage());
            return Intent.GENERAL;
        } finally {
            TokenUsageAdvisor.clearCurrentCallType();
        }
    }

    /**
     * 意图枚举
     */
    public enum Intent {
        /** 需要检索旅行知识库 */
        RAG,
        /** 通用对话，不需要 RAG */
        GENERAL
    }
}
