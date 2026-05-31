package com.tripagent.model.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Chat response DTO
 */
@Data
@Builder
public class ChatResponse {

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Response type: thinking, planning, executing, result, error
     */
    private String type;

    /**
     * Response content
     */
    private Object content;

    /**
     * Timestamp
     */
    private LocalDateTime timestamp;
}
