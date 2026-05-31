package com.tripagent.service.impl;

import com.tripagent.service.ChatMemoryService;
import com.tripagent.service.MemoryCompressionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 记忆压缩服务实现
 *
 * 压缩策略：当消息数量达到窗口大小的 60% 时触发压缩
 */
@Slf4j
@Service
public class MemoryCompressionServiceImpl implements MemoryCompressionService {

    private final ChatModel planningChatModel;
    private final ChatMemoryService chatMemoryService;

    @Value("${trip.agent.memory.window-size:20}")
    private int windowSize;

    @Value("${trip.agent.memory.keep-recent:10}")
    private int keepRecent;

    @Value("${trip.agent.memory.compress-gap:5}")
    private int compressGap;

    /**
     * 压缩触发比例：60%
     */
    private static final double COMPRESS_RATIO = 0.6;

    /**
     * 记录正在压缩的用户，避免并发重复压缩
     */
    private final ConcurrentHashMap<String, Boolean> compressing = new ConcurrentHashMap<>();

    public MemoryCompressionServiceImpl(
            @Qualifier("planningChatModel") ChatModel planningChatModel,
            ChatMemoryService chatMemoryService) {
        this.planningChatModel = planningChatModel;
        this.chatMemoryService = chatMemoryService;
    }

    @Override
    public List<Message> compressIfNeeded(String userId, List<Message> messages) {
        int compressThreshold = (int) (windowSize * COMPRESS_RATIO);

        // 如果消息数量未达到阈值，不压缩
        if (messages.size() <= compressThreshold) {
            return messages;
        }

        // 使用 putIfAbsent 保证原子性，避免并发重复压缩
        if (compressing.putIfAbsent(userId, true) != null) {
            log.debug("用户 {} 正在压缩中，跳过", userId);
            return messages;
        }

        try {
            return doCompress(userId, messages);
        } finally {
            compressing.remove(userId);
        }
    }

    /**
     * 执行压缩
     */
    private List<Message> doCompress(String userId, List<Message> messages) {
        log.info("触发记忆压缩: userId={}, 消息数={}, 阈值={}(窗口{}的60%)",
                userId, messages.size(), (int) (windowSize * COMPRESS_RATIO), windowSize);

        // 分离旧消息和新消息
        List<Message> oldMessages = messages.subList(0, messages.size() - keepRecent);
        List<Message> recentMessages = new ArrayList<>(messages.subList(messages.size() - keepRecent, messages.size()));

        // 用 Pro 模型生成摘要
        String summary = generateSummary(oldMessages);

        // 持久化：事务性清除旧消息 + 写入摘要和最近消息
        persistCompressedMessages(userId, summary, recentMessages);

        // 构建压缩后的消息列表（用于返回给调用方）
        List<Message> compressed = new ArrayList<>();
        compressed.add(new SystemMessage("历史对话摘要: " + summary));
        compressed.addAll(recentMessages);

        log.info("压缩完成: 原消息数={}, 压缩后消息数={}", messages.size(), compressed.size());

        return compressed;
    }

    /**
     * 持久化压缩后的消息到数据库（事务性原子操作）
     */
    private void persistCompressedMessages(String userId, String summary, List<Message> recentMessages) {
        try {
            // 构建完整的新消息列表：摘要 + 最近消息
            List<Message> newMessages = new ArrayList<>();
            newMessages.add(new SystemMessage("历史对话摘要: " + summary));
            newMessages.addAll(recentMessages);

            // 事务性批量替换：清除旧数据 + 写入新数据在同一事务内完成
            chatMemoryService.clearAndSaveAll(userId, newMessages);

            log.info("压缩消息已持久化: userId={}", userId);
        } catch (Exception e) {
            log.error("持久化压缩消息失败: userId={}", userId, e);
        }
    }

    /**
     * 使用 Pro 模型生成对话摘要
     */
    private String generateSummary(List<Message> messages) {
        String conversationText = messages.stream()
                .map(m -> {
                    if (m instanceof UserMessage) {
                        return "用户: " + m.getText();
                    } else if (m instanceof AssistantMessage) {
                        return "助手: " + m.getText();
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));

        String prompt = """
                请将以下对话压缩为不超过200字的摘要，保留关键信息：
                1. 用户的目的地偏好
                2. 预算信息
                3. 旅行日期
                4. 已查询的天气/景点/酒店信息
                5. 用户的特殊要求

                对话内容：
                %s
                """.formatted(conversationText);

        try {
            return planningChatModel.call(new Prompt(prompt))
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            log.error("生成摘要失败: {}", e.getMessage());
            return "（摘要生成失败）";
        }
    }
}
