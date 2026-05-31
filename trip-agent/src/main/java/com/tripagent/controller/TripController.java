package com.tripagent.controller;

import com.tripagent.agent.TripAgent;
import com.tripagent.agent.core.AgentContext;
import com.tripagent.agent.core.SseEventEmitter;
import com.tripagent.model.dto.ChatRequest;
import com.tripagent.service.SessionManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

/**
 * Unified API endpoint for Trip Agent.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class TripController {

    private final TripAgent tripAgent;
    private final SessionManager sessionManager;
    private final SseEventEmitter sseEventEmitter;

    /**
     * Unified chat endpoint with SSE streaming
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request from user: {}", request.getUserId());

        // Manual validation for SSE endpoint (Spring validation may not work properly with SseEmitter)
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        // Generate session ID if not provided
        final String finalSessionId;
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            finalSessionId = UUID.randomUUID().toString();
        } else {
            finalSessionId = request.getSessionId();
        }

        // Get or create session
        SessionManager.SessionData session = sessionManager.getOrCreateSession(
                request.getUserId(), finalSessionId);

        // Add user message to history
        sessionManager.addMessage(finalSessionId, "user", request.getMessage());

        // Create SSE emitter
        SseEmitter emitter = sseEventEmitter.createEmitter(finalSessionId);

        // Build agent context
        AgentContext context = AgentContext.builder()
                .userId(request.getUserId())
                .sessionId(finalSessionId)
                .userMessage(request.getMessage())
                .chatHistory(session.getChatHistory())
                .build();

        // Execute agent asynchronously
        tripAgent.execute(context)
                .doOnNext(step -> {
                    // Steps are already sent via SseEventEmitter in TripAgent
                    log.debug("Step: {} - {}", step.getType(), step.getContent());
                })
                .doOnError(e -> {
                    log.error("Agent execution failed", e);
                    sseEventEmitter.sendError(finalSessionId, "Execution failed: " + e.getMessage());
                    sseEventEmitter.complete(finalSessionId);
                })
                .doOnComplete(() -> {
                    log.info("Agent execution completed for session: {}", finalSessionId);
                    sseEventEmitter.complete(finalSessionId);
                })
                .subscribe();

        return emitter;
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * Get active session count
     */
    @GetMapping("/sessions")
    public int getActiveSessions() {
        return sessionManager.getActiveSessionCount();
    }
}
