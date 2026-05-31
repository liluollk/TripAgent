package com.tripagent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Chat request DTO
 */
@Data
public class ChatRequest {

    /**
     * User ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * Session ID (optional, will be generated if not provided)
     */
    private String sessionId;

    /**
     * User message
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;
}
