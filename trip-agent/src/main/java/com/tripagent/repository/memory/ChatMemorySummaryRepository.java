package com.tripagent.repository.memory;

import com.tripagent.model.entity.ChatMemorySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMemorySummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ChatMemorySummary> rowMapper = (rs, rowNum) -> {
        ChatMemorySummary summary = new ChatMemorySummary();
        summary.setId(rs.getLong("id"));
        summary.setConversationId(rs.getString("conversation_id"));
        summary.setSummary(rs.getString("summary"));
        summary.setMessageRange(rs.getString("message_range"));
        summary.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return summary;
    };

    public void save(ChatMemorySummary summary) {
        String sql = "INSERT INTO chat_memory_summary (conversation_id, summary, message_range, created_at) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                summary.getConversationId(),
                summary.getSummary(),
                summary.getMessageRange(),
                Timestamp.valueOf(summary.getCreatedAt())
        );
    }

    public List<ChatMemorySummary> findByConversationId(String conversationId) {
        String sql = "SELECT * FROM chat_memory_summary WHERE conversation_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    public void deleteByConversationId(String conversationId) {
        String sql = "DELETE FROM chat_memory_summary WHERE conversation_id = ?";
        jdbcTemplate.update(sql, conversationId);
    }
}
