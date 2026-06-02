package com.tripagent.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SSE (Server-Sent Events) emitters for streaming responses.
 */
@Slf4j
@Component
public class SseEventEmitter {

    /**
     * Maximum emitter lifetime in milliseconds (10 minutes)
     */
    private static final long MAX_EMITTER_LIFETIME_MS = 10 * 60 * 1000;

    /**
     * Active emitters by session ID
     */
    private final ConcurrentHashMap<String, EmitterWrapper> emitters = new ConcurrentHashMap<>();

    /**
     * Wrapper to track emitter creation time
     */
    private record EmitterWrapper(SseEmitter emitter, long createdAt) {}

    /**
     * Create a new SSE emitter for a session
     */
    public SseEmitter createEmitter(String sessionId) {
        // Remove old emitter if exists
        EmitterWrapper oldWrapper = emitters.remove(sessionId);
        if (oldWrapper != null) {
            oldWrapper.emitter().complete();
        }

        // Create new emitter with no timeout
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(sessionId, new EmitterWrapper(emitter, System.currentTimeMillis()));

        // Cleanup on completion
        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            log.debug("SSE emitter completed for session: {}", sessionId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.warn("SSE emitter timed out for session: {}", sessionId);
        });

        emitter.onError(e -> {
            emitters.remove(sessionId);
            log.error("SSE emitter error for session: {}", sessionId, e);
        });

        return emitter;
    }

    /**
     * Send an event to a session
     */
    public void sendEvent(String sessionId, String eventType, Object data) {
        EmitterWrapper wrapper = emitters.get(sessionId);
        if (wrapper == null) {
            log.warn("No emitter found for session: {}", sessionId);
            return;
        }

        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventType)
                    .data(data);
            wrapper.emitter().send(event);
            log.debug("Sent event to session {}: {} - {}", sessionId, eventType, data);
        } catch (IOException e) {
            log.error("Failed to send event to session: {}", sessionId, e);
            wrapper.emitter().completeWithError(e);
            emitters.remove(sessionId);
        }
    }

    /**
     * Send a thinking event
     */
    public void sendThinking(String sessionId, String content) {
        sendEvent(sessionId, "thinking", content);
    }

    /**
     * Send a planning event
     */
    public void sendPlanning(String sessionId, Object plan) {
        sendEvent(sessionId, "planning", plan);
    }

    /**
     * Send an executing event
     */
    public void sendExecuting(String sessionId, String step, String status) {
        sendEvent(sessionId, "executing", new StepEvent(step, status));
    }

    /**
     * Send a result event
     */
    public void sendResult(String sessionId, Object result) {
        sendEvent(sessionId, "result", result);
    }

    /**
     * Send an error event
     */
    public void sendError(String sessionId, String error) {
        sendEvent(sessionId, "error", error);
    }

    /**
     * Send a tool call event (for ReAct ACT step)
     */
    public void sendToolCall(String sessionId, String toolName, String toolInput) {
        sendEvent(sessionId, "tool_call", new ToolCallEvent(toolName, toolInput));
    }

    /**
     * Send a tool result event (for ReAct OBSERVE step)
     */
    public void sendToolResult(String sessionId, String toolOutput) {
        sendEvent(sessionId, "tool_result", toolOutput);
    }

    /**
     * Complete the emitter for a session
     */
    public void complete(String sessionId) {
        EmitterWrapper wrapper = emitters.remove(sessionId);
        if (wrapper != null) {
            wrapper.emitter().complete();
        }
    }

    /**
     * Scheduled cleanup of stale emitters (runs every 60 seconds)
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupStaleEmitters() {
        long now = System.currentTimeMillis();
        int cleaned = 0;

        // 使用 removeIf 避免遍历中修改 ConcurrentHashMap 的问题
        var iterator = emitters.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().createdAt() > MAX_EMITTER_LIFETIME_MS) {
                entry.getValue().emitter().complete();
                iterator.remove();
                cleaned++;
                log.warn("Cleaned up stale SSE emitter for session: {} (age: {}ms)",
                        entry.getKey(), now - entry.getValue().createdAt());
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} stale SSE emitters, {} active remaining", cleaned, emitters.size());
        }
    }

    /**
     * Get active emitter count
     */
    public int getActiveEmitterCount() {
        return emitters.size();
    }

    /**
     * Step event DTO
     */
    public record StepEvent(String step, String status) {}

    /**
     * Tool call event DTO
     */
    public record ToolCallEvent(String toolName, String toolInput) {}
}
