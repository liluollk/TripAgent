package com.tripagent.service;

import com.tripagent.agent.core.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
     * 单个会话最大消息数（防止内存无限增长）
     */
    private static final int MAX_CHAT_HISTORY_SIZE = 200;

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
     * 定期清理超时会话（每 10 分钟执行一次，清理超过 2 小时未活动的会话）
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredSessions() {
        LocalDateTime threshold = LocalDateTime.now().minus(2, ChronoUnit.HOURS);
        int cleaned = 0;

        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getLastActivityAt().isBefore(threshold)) {
                iterator.remove();
                cleaned++;
                log.info("清理过期会话: {} (用户: {})", entry.getKey(), entry.getValue().getUserId());
            }
        }

        if (cleaned > 0) {
            log.info("清理了 {} 个过期会话，当前活跃会话: {}", cleaned, sessions.size());
        }
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
            // 防止内存无限增长：超出限制时移除最早的消息
            while (chatHistory.size() > MAX_CHAT_HISTORY_SIZE) {
                chatHistory.remove(0);
            }
            this.lastActivityAt = LocalDateTime.now();
        }

        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public List<AgentContext.ChatMessage> getChatHistory() { return chatHistory; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    }
}
