package com.tripagent.exception;

/**
 * 可重试的 LLM 调用异常
 * 仅网络超时和服务端错误（5xx）应触发重试
 */
public class RetryableLlmException extends RuntimeException {

    public RetryableLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
