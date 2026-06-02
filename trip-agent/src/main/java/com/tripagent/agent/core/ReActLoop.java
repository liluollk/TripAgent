package com.tripagent.agent.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.tripagent.service.TokenUsageTracker.CallType;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct (Reasoning + Acting) 循环实现。
 *
 * 使用 Spring AI 原生工具调用机制（而非文本解析）：
 * - ChatClient 配置 defaultTools() 后，LLM 自动决定调用哪些工具
 * - Spring AI 自动执行工具并将结果回传给 LLM
 * - 整个 Think → Act → Observe 循环由 Spring AI 内部管理
 * - 通过 ToolEventCapture 捕获工具调用事件用于 SSE 推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActLoop {

    private final LlmClient llmClient;

    /**
     * 执行 ReAct 循环
     *
     * @param chatClient   已配置 defaultTools() 的 ChatClient
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param chatHistory  对话历史
     * @param sessionId    会话 ID（用于 SSE 事件追踪）
     * @param callType     调用类型（用于 Token 追踪）
     * @return AgentStep 流
     */
    public Flux<AgentStep> execute(
            ChatClient chatClient,
            String systemPrompt,
            String userMessage,
            List<AgentContext.ChatMessage> chatHistory,
            String sessionId,
            CallType callType) {

        // 构建消息列表
        List<Message> messages = buildMessages(systemPrompt, userMessage, chatHistory);

        // 在 boundedElastic 线程池上执行（避免阻塞 Reactor 线程）
        // 注意：ThreadLocal 必须在执行线程内设置，否则跨线程后丢失
        Mono<ChatResponse> llmCall = Mono.fromCallable(() -> {
            ToolEventCapture.setSessionId(sessionId);
            TokenUsageAdvisor.setCurrentCallType(callType);
            return llmClient.call(chatClient, messages);
        }).subscribeOn(Schedulers.boundedElastic());

        return llmCall.flatMapMany(response -> {
            AssistantMessage assistantMessage = response.getResult().getOutput();
            String assistantContent = assistantMessage.getText();

            // 收集 ToolEventCapture 捕获的工具调用事件
            List<AgentStep> toolEvents = ToolEventCapture.collectEvents();

            // 构建 THINK 步骤（LLM 的思考/推理文本）
            Flux<AgentStep> thinkSteps = (assistantContent != null && !assistantContent.isBlank())
                    ? Flux.just(AgentStep.builder()
                            .type(AgentStep.StepType.THINK)
                            .content(assistantContent)
                            .build())
                    : Flux.empty();

            // 构建工具事件流
            Flux<AgentStep> toolSteps = Flux.fromIterable(toolEvents);

            // 构建最终结果步骤
            AgentStep resultStep = AgentStep.builder()
                    .type(AgentStep.StepType.RESULT)
                    .content(assistantContent)
                    .build();

            return Flux.concat(thinkSteps, toolSteps, Flux.just(resultStep));
        }).doFinally(signal -> {
            // 清除 ThreadLocal
            ToolEventCapture.cleanup();
            TokenUsageAdvisor.clearCurrentCallType();
        }).onErrorResume(e -> {
            log.error("ReAct 循环执行失败", e);
            ToolEventCapture.cleanup();
            TokenUsageAdvisor.clearCurrentCallType();
            return Flux.just(AgentStep.builder()
                    .type(AgentStep.StepType.ERROR)
                    .content("执行失败: " + e.getMessage())
                    .build());
        });
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessages(
            String systemPrompt, String userMessage, List<AgentContext.ChatMessage> chatHistory) {

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        if (chatHistory != null) {
            for (AgentContext.ChatMessage msg : chatHistory) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        messages.add(new UserMessage(userMessage));
        return messages;
    }
}
