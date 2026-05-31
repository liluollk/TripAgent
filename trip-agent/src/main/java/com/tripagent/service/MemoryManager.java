package com.tripagent.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆管理器 - 统一处理记忆的加载和保存（门面模式）
 */
@Slf4j
@Service
public class MemoryManager {

    private final ChatMemoryService chatMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final MemoryCompressionService memoryCompressionService;
    private final MemoryExtractionService memoryExtractionService;

    public MemoryManager(
            ChatMemoryService chatMemoryService,
            LongTermMemoryService longTermMemoryService,
            MemoryCompressionService memoryCompressionService,
            MemoryExtractionService memoryExtractionService) {
        this.chatMemoryService = chatMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.memoryCompressionService = memoryCompressionService;
        this.memoryExtractionService = memoryExtractionService;
    }

    /**
     * 记忆上下文
     */
    @Data
    public static class MemoryContext {
        private String longTermContext;  // 长期记忆（用户偏好）
        private List<Message> history;   // 压缩后的历史消息
    }

    /**
     * 加载记忆（短期 + 长期 + 压缩）
     */
    public MemoryContext load(String userId) {
        MemoryContext ctx = new MemoryContext();

        // 1. 加载长期记忆
        ctx.longTermContext = longTermMemoryService.getMemoryContext(userId);

        // 2. 加载短期记忆并压缩
        List<Message> history = chatMemoryService.getMessages(userId);
        ctx.history = memoryCompressionService.compressIfNeeded(userId, history);

        return ctx;
    }

    /**
     * 保存记忆（对话 + 偏好提取）
     */
    public void save(String userId, String userMessage, String reply) {
        // 1. 保存对话记忆
        try {
            chatMemoryService.addUserMessage(userId, userMessage);
            chatMemoryService.addAssistantMessage(userId, reply);
        } catch (Exception e) {
            log.error("保存对话记忆失败: {}", e.getMessage());
        }

        // 2. 提取并保存长期记忆
        try {
            MemoryExtractionService.ExtractionResult extraction = memoryExtractionService.extract(userMessage);

            if (extraction.preferences() != null && !extraction.preferences().isEmpty()) {
                longTermMemoryService.updatePreferences(userId, extraction.preferences());
            }

            if (extraction.city() != null && !extraction.city().isEmpty()) {
                longTermMemoryService.addVisitedCity(userId, extraction.city());
            }
        } catch (Exception e) {
            log.error("提取长期记忆失败: {}", e.getMessage());
        }
    }
}
