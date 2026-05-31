package com.tripagent.agent.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentContext 测试")
class AgentContextTest {

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("基本构建")
        void basicBuild() {
            AgentContext context = AgentContext.builder()
                    .userId("user1")
                    .sessionId("session1")
                    .userMessage("去南京玩3天")
                    .build();

            assertEquals("user1", context.getUserId());
            assertEquals("session1", context.getSessionId());
            assertEquals("去南京玩3天", context.getUserMessage());
            assertNull(context.getChatHistory());
            assertNull(context.getRequirements());
            assertNull(context.getCurrentPlan());
            assertNull(context.getCurrentStepIndex());
            assertNull(context.getStepResults());
        }

        @Test
        @DisplayName("完整构建")
        void fullBuild() {
            List<AgentContext.ChatMessage> chatHistory = Arrays.asList(
                    AgentContext.ChatMessage.builder()
                            .role("user")
                            .content("你好")
                            .build()
            );

            Map<String, Object> requirements = new HashMap<>();
            requirements.put("destination", "南京");
            requirements.put("days", 3);

            List<Object> stepResults = Arrays.asList("步骤1完成", "步骤2完成");

            AgentContext context = AgentContext.builder()
                    .userId("user1")
                    .sessionId("session1")
                    .userMessage("去南京玩3天")
                    .chatHistory(chatHistory)
                    .requirements(requirements)
                    .currentPlan("计划内容")
                    .currentStepIndex(2)
                    .stepResults(stepResults)
                    .build();

            assertEquals("user1", context.getUserId());
            assertEquals("session1", context.getSessionId());
            assertEquals("去南京玩3天", context.getUserMessage());
            assertEquals(1, context.getChatHistory().size());
            assertEquals(2, context.getRequirements().size());
            assertEquals("计划内容", context.getCurrentPlan());
            assertEquals(2, context.getCurrentStepIndex());
            assertEquals(2, context.getStepResults().size());
        }
    }

    @Nested
    @DisplayName("ChatMessage")
    class ChatMessage {

        @Test
        @DisplayName("构建用户消息")
        void buildUserMessage() {
            AgentContext.ChatMessage message = AgentContext.ChatMessage.builder()
                    .role("user")
                    .content("你好")
                    .build();

            assertEquals("user", message.getRole());
            assertEquals("你好", message.getContent());
        }

        @Test
        @DisplayName("构建助手消息")
        void buildAssistantMessage() {
            AgentContext.ChatMessage message = AgentContext.ChatMessage.builder()
                    .role("assistant")
                    .content("你好！有什么可以帮助你的吗？")
                    .build();

            assertEquals("assistant", message.getRole());
            assertEquals("你好！有什么可以帮助你的吗？", message.getContent());
        }
    }

    @Nested
    @DisplayName("Data 注解")
    class DataAnnotation {

        @Test
        @DisplayName("getter 和 setter 正常工作")
        void gettersAndSetters() {
            AgentContext context = AgentContext.builder()
                    .userId("user1")
                    .sessionId("session1")
                    .userMessage("去南京玩3天")
                    .build();

            // 测试 setter
            context.setUserId("user2");
            context.setSessionId("session2");
            context.setUserMessage("去杭州玩5天");
            context.setCurrentStepIndex(3);

            // 测试 getter
            assertEquals("user2", context.getUserId());
            assertEquals("session2", context.getSessionId());
            assertEquals("去杭州玩5天", context.getUserMessage());
            assertEquals(3, context.getCurrentStepIndex());
        }
    }
}
