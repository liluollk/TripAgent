package com.tripagent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Chat request DTO
 */
@Data
@Schema(description = "聊天请求参数")
public class ChatRequest {

    /**
     * User ID
     */
    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "用户ID", example = "user-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * Session ID (optional, will be generated if not provided)
     */
    @Schema(description = "会话ID（可选，不传则自动生成）", example = "session-abc123")
    private String sessionId;

    /**
     * User message
     */
    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "用户消息内容", example = "3天成都旅行，预算5000，想吃火锅看熊猫", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
