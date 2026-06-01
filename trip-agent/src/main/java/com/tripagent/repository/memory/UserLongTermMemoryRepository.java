package com.tripagent.repository.memory;

import com.tripagent.model.entity.UserLongTermMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserLongTermMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserLongTermMemory> rowMapper = (rs, rowNum) -> {
        UserLongTermMemory memory = new UserLongTermMemory();
        memory.setId(rs.getLong("id"));
        memory.setConversationId(rs.getString("conversation_id"));
        memory.setMemoryType(rs.getString("memory_type"));
        memory.setContent(rs.getString("content"));
        memory.setConfidence(rs.getDouble("confidence"));
        memory.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return memory;
    };

    public void save(UserLongTermMemory memory) {
        String sql = "INSERT INTO user_long_term_memory (conversation_id, memory_type, content, confidence, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                memory.getConversationId(),
                memory.getMemoryType(),
                memory.getContent(),
                memory.getConfidence(),
                Timestamp.valueOf(memory.getCreatedAt())
        );
    }

    public List<UserLongTermMemory> findByConversationId(String conversationId) {
        String sql = "SELECT * FROM user_long_term_memory WHERE conversation_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    public List<UserLongTermMemory> findByConversationIdAndType(String conversationId, String memoryType) {
        String sql = "SELECT * FROM user_long_term_memory WHERE conversation_id = ? AND memory_type = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, conversationId, memoryType);
    }

    public void deleteByConversationId(String conversationId) {
        String sql = "DELETE FROM user_long_term_memory WHERE conversation_id = ?";
        jdbcTemplate.update(sql, conversationId);
    }
}
