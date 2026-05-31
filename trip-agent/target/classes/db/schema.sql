-- Spring AI will auto-create the table
-- This file is for reference only

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(255) NOT NULL,
    message_index INT NOT NULL,
    message_content TEXT,
    message_type VARCHAR(50),
    PRIMARY KEY (conversation_id, message_index)
);
