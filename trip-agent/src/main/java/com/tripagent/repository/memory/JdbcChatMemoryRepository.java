package com.tripagent.repository.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY",
                String.class
        );
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
                "SELECT message_content, message_type FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY message_index",
                (rs, rowNum) -> {
                    String content = rs.getString("message_content");
                    String type = rs.getString("message_type");
                    return createMessage(type, content);
                },
                conversationId
        );
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        // 查询现有消息数量
        int existingCount = countByConversationId(conversationId);

        // 如果消息数量相同，无需更新
        if (existingCount == messages.size()) {
            return;
        }

        // 只插入新增的消息
        String sql = "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, message_index, message_content, message_type) VALUES (?, ?, ?, ?)";
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = existingCount; i < messages.size(); i++) {
            Message message = messages.get(i);
            String type = getMessageType(message);
            String content = getTextContent(message);
            batchArgs.add(new Object[]{conversationId, i, content, type});
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }
    }

    /**
     * 查询对话的消息数量
     */
    private int countByConversationId(String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                Integer.class,
                conversationId
        );
        return count != null ? count : 0;
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", conversationId);
    }

    private String getMessageType(Message message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AssistantMessage) return "ASSISTANT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return "UNKNOWN";
    }

    private Message createMessage(String type, String content) {
        return switch (type) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    private String getTextContent(Message message) {
        if (message instanceof AbstractMessage) {
            return ((AbstractMessage) message).getText();
        }
        return message.toString();
    }
}
