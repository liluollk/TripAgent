-- 对话摘要表：存储压缩后的对话摘要
CREATE TABLE chat_memory_summary (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    message_range VARCHAR(50),  -- 摘要涵盖的消息范围，如 "0-15"
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_memory_summary_conversation_id ON chat_memory_summary(conversation_id);

-- 用户长期记忆表：存储用户偏好、事实、约束
CREATE TABLE user_long_term_memory (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    memory_type VARCHAR(50) NOT NULL,   -- PREFERENCE / FACT / CONSTRAINT
    content TEXT NOT NULL,
    confidence FLOAT DEFAULT 0.8,       -- 置信度 0-1
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_long_term_memory_conversation_id ON user_long_term_memory(conversation_id);
CREATE INDEX idx_user_long_term_memory_type ON user_long_term_memory(memory_type);
