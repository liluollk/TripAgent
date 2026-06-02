package com.tripagent.agent.core;

import com.tripagent.exception.RetryableLlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.List;

/**
 * LLM 调用客户端
 * 封装 ChatClient 调用逻辑，支持自动重试。
 *
 * 独立为 @Component 是为了保证 @Retryable 通过 Spring AOP 代理生效。
 * 如果在同一个类内部调用带 @Retryable 的方法，会因为自调用不走代理而导致重试失效。
 */
@Slf4j
@Component
public class LlmClient {

    /**
     * 调用 LLM（带自动重试）
     *
     * 最多重试 3 次，初始延迟 1 秒，指数退避
     * 仅在网络超时和服务端错误（5xx）时重试，参数错误等不可重试异常直接抛出
     *
     * @param chatClient 已配置好模型和工具的 ChatClient
     * @param messages   消息列表
     * @return ChatResponse
     */
    @Retryable(
        retryFor = {RetryableLlmException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ChatResponse call(ChatClient chatClient, List<Message> messages) {
        try {
            Prompt prompt = new Prompt(messages);
            return chatClient.prompt(prompt).call().chatResponse();
        } catch (ResourceAccessException e) {
            // 网络连接错误（超时、连接拒绝等）→ 可重试
            throw new RetryableLlmException("LLM 网络调用失败: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            // 服务端 5xx 错误 → 可重试
            throw new RetryableLlmException("LLM 服务端错误 (" + e.getStatusCode() + "): " + e.getMessage(), e);
        }
    }
}
