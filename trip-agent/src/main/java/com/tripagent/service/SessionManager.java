package com.tripagent.service;

import com.tripagent.agent.core.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages user sessions for multi-user support.
 */
@Slf4j
@Component
public class SessionManager {

    /**
     * Active sessions: sessionId -> SessionData
     */
    private final ConcurrentHashMap<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Get or create session
     */
    public SessionData getOrCreateSession(String userId, String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            log.info("Creating new session: {} for user: {}", id, userId);
            return new SessionData(userId, id);
        });
    }

    /**
     * Get session
     */
    public SessionData getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Add message to session history
     */
    public void addMessage(String sessionId, String role, String content) {
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            session.addChatMessage(role, content);
        }
    }

    /**
     * Remove session
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Removed session: {}", sessionId);
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Session data
     */
    public static class SessionData {
        private final String userId;
        private final String sessionId;
        private final List<AgentContext.ChatMessage> chatHistory = new CopyOnWriteArrayList<>();
        private final LocalDateTime createdAt;
        private LocalDateTime lastActivityAt;

        public SessionData(String userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.createdAt = LocalDateTime.now();
            this.lastActivityAt = this.createdAt;
        }

        public void addChatMessage(String role, String content) {
            chatHistory.add(AgentContext.ChatMessage.builder()
                    .role(role)
                    .content(content)
                    .build());
            this.lastActivityAt = LocalDateTime.now();
        }

        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public List<AgentContext.ChatMessage> getChatHistory() { return chatHistory; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    }
}
