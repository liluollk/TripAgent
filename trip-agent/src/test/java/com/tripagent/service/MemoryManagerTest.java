package com.tripagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryManager 测试")
class MemoryManagerTest {

    @Mock
    private ChatMemoryService chatMemoryService;

    @Mock
    private LongTermMemoryService longTermMemoryService;

    @Mock
    private MemoryCompressionService memoryCompressionService;

    @Mock
    private MemoryExtractionService memoryExtractionService;

    @InjectMocks
    private MemoryManager memoryManager;

    @Nested
    @DisplayName("load")
    class Load {

        @Test
        @DisplayName("加载记忆上下文")
        void loadMemoryContext() {
            // Arrange
            String userId = "user1";
            String longTermContext = "用户喜欢自然风景";
            List<Message> history = Arrays.asList(new AssistantMessage("你好"));
            List<Message> compressed = Arrays.asList(new AssistantMessage("你好"));

            when(longTermMemoryService.getMemoryContext(userId)).thenReturn(longTermContext);
            when(chatMemoryService.getMessages(userId)).thenReturn(history);
            when(memoryCompressionService.compressIfNeeded(userId, history)).thenReturn(compressed);

            // Act
            MemoryManager.MemoryContext ctx = memoryManager.load(userId);

            // Assert
            assertNotNull(ctx);
            assertEquals(longTermContext, ctx.getLongTermContext());
            assertEquals(compressed, ctx.getHistory());

            verify(longTermMemoryService).getMemoryContext(userId);
            verify(chatMemoryService).getMessages(userId);
            verify(memoryCompressionService).compressIfNeeded(userId, history);
        }

        @Test
        @DisplayName("长期记忆为空时")
        void loadWithEmptyLongTermMemory() {
            // Arrange
            String userId = "user1";
            List<Message> history = Arrays.asList(new AssistantMessage("你好"));

            when(longTermMemoryService.getMemoryContext(userId)).thenReturn("");
            when(chatMemoryService.getMessages(userId)).thenReturn(history);
            when(memoryCompressionService.compressIfNeeded(userId, history)).thenReturn(history);

            // Act
            MemoryManager.MemoryContext ctx = memoryManager.load(userId);

            // Assert
            assertNotNull(ctx);
            assertEquals("", ctx.getLongTermContext());
            assertEquals(history, ctx.getHistory());
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("保存对话和提取记忆")
        void saveAndExtractMemory() {
            // Arrange
            String userId = "user1";
            String userMessage = "我想去杭州玩";
            String reply = "杭州是个好地方！";

            MemoryExtractionService.ExtractionResult extraction = new MemoryExtractionService.ExtractionResult(
                    "杭州",
                    "喜欢自然风景"
            );

            when(memoryExtractionService.extract(userMessage)).thenReturn(extraction);

            // Act
            memoryManager.save(userId, userMessage, reply);

            // Assert
            verify(chatMemoryService).addUserMessage(userId, userMessage);
            verify(chatMemoryService).addAssistantMessage(userId, reply);
            verify(memoryExtractionService).extract(userMessage);
            verify(longTermMemoryService).updatePreferences(userId, extraction.preferences());
            verify(longTermMemoryService).addVisitedCity(userId, extraction.city());
        }

        @Test
        @DisplayName("提取结果为空时不更新长期记忆")
        void saveWithEmptyExtraction() {
            // Arrange
            String userId = "user1";
            String userMessage = "你好";
            String reply = "你好！";

            MemoryExtractionService.ExtractionResult extraction = new MemoryExtractionService.ExtractionResult(
                    null,
                    null
            );

            when(memoryExtractionService.extract(userMessage)).thenReturn(extraction);

            // Act
            memoryManager.save(userId, userMessage, reply);

            // Assert
            verify(chatMemoryService).addUserMessage(userId, userMessage);
            verify(chatMemoryService).addAssistantMessage(userId, reply);
            verify(memoryExtractionService).extract(userMessage);
            verify(longTermMemoryService, never()).updatePreferences(any(), any());
            verify(longTermMemoryService, never()).addVisitedCity(any(), any());
        }

        @Test
        @DisplayName("保存对话记忆失败不影响后续处理")
        void saveWithChatMemoryFailure() {
            // Arrange
            String userId = "user1";
            String userMessage = "我想去杭州玩";
            String reply = "杭州是个好地方！";

            doThrow(new RuntimeException("数据库异常")).when(chatMemoryService).addUserMessage(any(), any());

            MemoryExtractionService.ExtractionResult extraction = new MemoryExtractionService.ExtractionResult(
                    "杭州",
                    "喜欢自然风景"
            );

            when(memoryExtractionService.extract(userMessage)).thenReturn(extraction);

            // Act & Assert - 不应抛出异常
            assertDoesNotThrow(() -> memoryManager.save(userId, userMessage, reply));

            verify(longTermMemoryService).updatePreferences(userId, extraction.preferences());
            verify(longTermMemoryService).addVisitedCity(userId, extraction.city());
        }

        @Test
        @DisplayName("提取长期记忆失败不影响对话保存")
        void saveWithExtractionFailure() {
            // Arrange
            String userId = "user1";
            String userMessage = "我想去杭州玩";
            String reply = "杭州是个好地方！";

            doThrow(new RuntimeException("提取异常")).when(memoryExtractionService).extract(any());

            // Act & Assert - 不应抛出异常
            assertDoesNotThrow(() -> memoryManager.save(userId, userMessage, reply));

            verify(chatMemoryService).addUserMessage(userId, userMessage);
            verify(chatMemoryService).addAssistantMessage(userId, reply);
        }
    }
}
