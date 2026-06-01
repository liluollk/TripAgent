package com.tripagent.agent.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) loop implementation.
 * Implements the Think -> Act -> Observe cycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActLoop {

    private final ToolRegistry toolRegistry;

    /**
     * Maximum iterations to prevent infinite loops
     */
    private static final int MAX_ITERATIONS = 10;

    /**
     * Pattern for tool call: ```tool\ntoolName:input\n```
     */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "```tool\\s*\\n([^:]+):(.+?)\\n```", Pattern.DOTALL);

    /**
     * Execute ReAct loop
     *
     * @param chatModel The chat model to use
     * @param systemPrompt System prompt for the agent
     * @param userMessage User's message
     * @param chatHistory Previous chat history
     * @return Flux of AgentStep
     */
    public Flux<AgentStep> execute(
            ChatModel chatModel,
            String systemPrompt,
            String userMessage,
            List<AgentContext.ChatMessage> chatHistory) {

        // Build messages list (non-blocking)
        List<Message> messages = buildMessages(systemPrompt, userMessage, chatHistory);

        // Use iterative reactive loop to avoid blocking Reactor threads
        return Flux.defer(() -> reactiveLoop(chatModel, messages, 0));
    }

    /**
     * Build the initial message list
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

    /**
     * 调用 LLM（带重试）
     *
     * 最多重试 3 次，间隔 1 秒，指数退避
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private ChatResponse callLLM(ChatModel chatModel, List<Message> messages) {
        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt);
    }

    /**
     * Recursive reactive loop - each iteration is non-blocking
     */
    private Flux<AgentStep> reactiveLoop(ChatModel chatModel, List<Message> messages, int iteration) {
        if (iteration >= MAX_ITERATIONS) {
            return Flux.just(AgentStep.builder()
                    .type(AgentStep.StepType.ERROR)
                    .content("Max iterations reached")
                    .build());
        }

        log.debug("ReAct iteration {}", iteration + 1);

        // Call LLM on boundedElastic thread pool (non-blocking for Reactor)
        Mono<ChatResponse> llmCall = Mono.fromCallable(() ->
            callLLM(chatModel, messages)
        ).subscribeOn(Schedulers.boundedElastic());

        return llmCall.flatMapMany(response -> {
            AssistantMessage assistantMessage = response.getResult().getOutput();
            String assistantContent = assistantMessage.getText();

            // Parse response for tool calls
            Matcher matcher = TOOL_CALL_PATTERN.matcher(assistantContent);
            if (matcher.find()) {
                // Extract thinking (text before tool call)
                String thinking = assistantContent.substring(0, matcher.start()).trim();

                Flux<AgentStep> thinkingStep = thinking.isEmpty()
                        ? Flux.empty()
                        : Flux.just(AgentStep.builder()
                                .type(AgentStep.StepType.THINK)
                                .content(thinking)
                                .build());

                // Extract tool call
                String toolName = matcher.group(1).trim();
                String toolInput = matcher.group(2).trim();

                // ACT step
                AgentStep actStep = AgentStep.builder()
                        .type(AgentStep.StepType.ACT)
                        .toolName(toolName)
                        .toolInput(toolInput)
                        .build();

                // Execute tool (may be blocking, so use boundedElastic)
                Mono<String> toolCall = Mono.fromCallable(() ->
                        toolRegistry.executeTool(toolName, toolInput)
                ).subscribeOn(Schedulers.boundedElastic());

                return toolCall.flatMapMany(toolOutput -> {
                    // OBSERVE step
                    AgentStep observeStep = AgentStep.builder()
                            .type(AgentStep.StepType.OBSERVE)
                            .toolOutput(toolOutput)
                            .build();

                    // Add to messages for next iteration
                    messages.add(new AssistantMessage(assistantContent));
                    messages.add(new UserMessage("Tool output: " + toolOutput));

                    // Continue loop recursively
                    Flux<AgentStep> nextIteration = reactiveLoop(chatModel, messages, iteration + 1);

                    return Flux.concat(thinkingStep, Flux.just(actStep), Flux.just(observeStep), nextIteration);
                }).onErrorResume(e -> {
                    log.error("Tool execution failed", e);
                    return Flux.just(AgentStep.builder()
                            .type(AgentStep.StepType.ERROR)
                            .content("Tool execution failed: " + e.getMessage())
                            .build());
                });

            } else {
                // No tool call - this is the final result
                return Flux.just(AgentStep.builder()
                        .type(AgentStep.StepType.RESULT)
                        .content(assistantContent)
                        .build());
            }
        }).onErrorResume(e -> {
            log.error("ReAct loop failed at iteration {}", iteration, e);
            return Flux.just(AgentStep.builder()
                    .type(AgentStep.StepType.ERROR)
                    .content("ReAct loop failed: " + e.getMessage())
                    .build());
        });
    }
}
