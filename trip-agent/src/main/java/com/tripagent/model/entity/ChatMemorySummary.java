package com.tripagent.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMemorySummary {
    private Long id;
    private String conversationId;
    private String summary;
    private String messageRange;
    private LocalDateTime createdAt;
}
