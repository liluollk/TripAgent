package com.tripagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager 测试")
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Nested
    @DisplayName("getOrCreateSession")
    class GetOrCreateSession {

        @Test
        @DisplayName("创建新会话")
        void createNewSession() {
            SessionManager.SessionData session = sessionManager.getOrCreateSession("user1", "session1");

            assertNotNull(session);
            assertEquals("user1", session.getUserId());
            assertEquals("session1", session.getSessionId());
            assertNotNull(session.getCreatedAt());
            assertNotNull(session.getLastActivityAt());
            assertTrue(session.getChatHistory().isEmpty());
        }

        @Test
        @DisplayName("获取已存在的会话")
        void getExistingSession() {
            SessionManager.SessionData session1 = sessionManager.getOrCreateSession("user1", "session1");
            SessionManager.SessionData session2 = sessionManager.getOrCreateSession("user1", "session1");

            assertSame(session1, session2);
        }

        @Test
        @DisplayName("不同 sessionId 创建不同会话")
        void differentSessions() {
            SessionManager.SessionData session1 = sessionManager.getOrCreateSession("user1", "session1");
            SessionManager.SessionData session2 = sessionManager.getOrCreateSession("user1", "session2");

            assertNotSame(session1, session2);
        }
    }

    @Nested
    @DisplayName("getSession")
    class GetSession {

        @Test
        @DisplayName("获取已存在的会话")
        void getExisting() {
            sessionManager.getOrCreateSession("user1", "session1");
            SessionManager.SessionData session = sessionManager.getSession("session1");

            assertNotNull(session);
            assertEquals("user1", session.getUserId());
        }

        @Test
        @DisplayName("获取不存在的会话返回 null")
        void getNonExisting() {
            SessionManager.SessionData session = sessionManager.getSession("non-existing");

            assertNull(session);
        }
    }

    @Nested
    @DisplayName("addMessage")
    class AddMessage {

        @Test
        @DisplayName("添加消息到会话")
        void addMessageToSession() {
            sessionManager.getOrCreateSession("user1", "session1");
            sessionManager.addMessage("session1", "user", "你好");

            SessionManager.SessionData session = sessionManager.getSession("session1");
            assertEquals(1, session.getChatHistory().size());
            assertEquals("user", session.getChatHistory().get(0).getRole());
            assertEquals("你好", session.getChatHistory().get(0).getContent());
        }

        @Test
        @DisplayName("添加多条消息")
        void addMultipleMessages() {
            sessionManager.getOrCreateSession("user1", "session1");
            sessionManager.addMessage("session1", "user", "你好");
            sessionManager.addMessage("session1", "assistant", "你好！有什么可以帮助你的吗？");

            SessionManager.SessionData session = sessionManager.getSession("session1");
            assertEquals(2, session.getChatHistory().size());
        }

        @Test
        @DisplayName("添加消息到不存在的会话不报错")
        void addMessageToNonExistingSession() {
            assertDoesNotThrow(() -> {
                sessionManager.addMessage("non-existing", "user", "你好");
            });
        }
    }

    @Nested
    @DisplayName("removeSession")
    class RemoveSession {

        @Test
        @DisplayName("移除会话")
        void removeSession() {
            sessionManager.getOrCreateSession("user1", "session1");
            sessionManager.removeSession("session1");

            assertNull(sessionManager.getSession("session1"));
        }

        @Test
        @DisplayName("移除不存在的会话不报错")
        void removeNonExistingSession() {
            assertDoesNotThrow(() -> {
                sessionManager.removeSession("non-existing");
            });
        }
    }

    @Nested
    @DisplayName("getActiveSessionCount")
    class GetActiveSessionCount {

        @Test
        @DisplayName("初始会话数为 0")
        void initialCountIsZero() {
            assertEquals(0, sessionManager.getActiveSessionCount());
        }

        @Test
        @DisplayName("创建会话后计数增加")
        void countIncreasesAfterCreation() {
            sessionManager.getOrCreateSession("user1", "session1");
            assertEquals(1, sessionManager.getActiveSessionCount());

            sessionManager.getOrCreateSession("user2", "session2");
            assertEquals(2, sessionManager.getActiveSessionCount());
        }

        @Test
        @DisplayName("移除会话后计数减少")
        void countDecreasesAfterRemoval() {
            sessionManager.getOrCreateSession("user1", "session1");
            sessionManager.getOrCreateSession("user2", "session2");
            assertEquals(2, sessionManager.getActiveSessionCount());

            sessionManager.removeSession("session1");
            assertEquals(1, sessionManager.getActiveSessionCount());
        }
    }

    @Nested
    @DisplayName("SessionData")
    class SessionData {

        @Test
        @DisplayName("会话创建时间")
        void sessionCreatedAt() {
            SessionManager.SessionData session = sessionManager.getOrCreateSession("user1", "session1");

            assertNotNull(session.getCreatedAt());
        }

        @Test
        @DisplayName("添加消息后更新 lastActivityAt")
        void lastActivityAtUpdated() {
            SessionManager.SessionData session = sessionManager.getOrCreateSession("user1", "session1");
            var before = session.getLastActivityAt();

            sessionManager.addMessage("session1", "user", "你好");
            var after = session.getLastActivityAt();

            assertNotNull(after);
            assertTrue(after.isAfter(before) || after.equals(before));
        }
    }
}
