package com.tripagent.service.memory;

import com.tripagent.agent.core.AgentContext;
import com.tripagent.model.entity.ChatMemorySummary;
import com.tripagent.repository.memory.ChatMemorySummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryCompressionService {

    private final ChatModel chatModel;
    private final ChatMemorySummaryRepository summaryRepository;

    public MemoryCompressionService(
            @Qualifier("planningChatModel") ChatModel chatModel,
            ChatMemorySummaryRepository summaryRepository) {
        this.chatModel = chatModel;
        this.summaryRepository = summaryRepository;
    }

    @Value("${trip.agent.memory.compression-threshold:15}")
    private int compressionThreshold;

    private static final String COMPRESSION_PROMPT = """
        请将以下对话历史压缩成简洁的摘要，保留关键信息：
        1. 用户的旅行偏好（目的地、预算、风格等）
        2. 已确认的行程安排
        3. 重要的约束条件
        4. 未解决的问题

        对话历史：
        %s

        请用中文输出摘要，格式：
        【摘要】
        [简洁的对话摘要]
        """;

    /**
     * 压缩对话历史
     * @param conversationId 会话ID
     * @param messages 待压缩的消息列表
     * @return 压缩后的摘要
     */
    public String compressMessages(String conversationId, List<AgentContext.ChatMessage> messages) {
        if (messages.size() <= compressionThreshold) {
            log.debug("消息数量 {} 未超过阈值 {}，跳过压缩", messages.size(), compressionThreshold);
            return null;
        }

        log.info("开始压缩对话 {}，消息数量: {}", conversationId, messages.size());

        // 提取需要压缩的旧消息（保留最近的消息）
        int keepRecent = 5;
        List<AgentContext.ChatMessage> toCompress = messages.subList(0, messages.size() - keepRecent);
        String messageText = formatMessages(toCompress);

        // 调用 LLM 生成摘要
        String prompt = String.format(COMPRESSION_PROMPT, messageText);
        String summary = chatModel.call(new Prompt(new UserMessage(prompt)))
                .getResult()
                .getOutput()
                .getText();

        // 保存摘要
        ChatMemorySummary summaryEntity = new ChatMemorySummary();
        summaryEntity.setConversationId(conversationId);
        summaryEntity.setSummary(summary);
        summaryEntity.setMessageRange("0-" + (messages.size() - keepRecent));
        summaryEntity.setCreatedAt(LocalDateTime.now());
        summaryRepository.save(summaryEntity);

        log.info("对话 {} 压缩完成，摘要已保存", conversationId);
        return summary;
    }

    /**
     * 获取对话的历史摘要
     */
    public String getHistoricalSummary(String conversationId) {
        List<ChatMemorySummary> summaries = summaryRepository.findByConversationId(conversationId);
        if (summaries.isEmpty()) {
            return "";
        }
        return summaries.stream()
                .map(ChatMemorySummary::getSummary)
                .collect(Collectors.joining("\n---\n"));
    }

    private String formatMessages(List<AgentContext.ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AgentContext.ChatMessage msg : messages) {
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }
}
