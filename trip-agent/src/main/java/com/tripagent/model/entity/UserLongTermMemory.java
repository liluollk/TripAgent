package com.tripagent.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserLongTermMemory {
    private Long id;
    private String conversationId;
    private String memoryType;
    private String content;
    private Double confidence;
    private LocalDateTime createdAt;
}
