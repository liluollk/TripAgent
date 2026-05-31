package com.tripagent.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 *
 * 错误码规则：
 * - 1xxx: AI 服务错误
 * - 2xxx: 外部 API 调用错误
 * - 3xxx: 业务逻辑错误
 * - 9xxx: 系统内部错误
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // AI 服务错误 1xxx
    AI_SERVICE_ERROR(1001, "AI 服务暂时不可用"),
    AI_RESPONSE_PARSE_ERROR(1002, "AI 响应解析失败"),
    AI_REQUEST_TIMEOUT(1003, "AI 请求超时"),

    // 外部 API 调用错误 2xxx
    WEATHER_API_ERROR(2001, "天气查询失败"),
    POI_API_ERROR(2002, "景点查询失败"),
    HOTEL_API_ERROR(2003, "酒店查询失败"),
    MCP_TOOL_ERROR(2004, "MCP 工具调用失败"),

    // 业务逻辑错误 3xxx
    INVALID_REQUEST(3001, "请求参数无效"),
    USER_NOT_FOUND(3002, "用户不存在"),
    CONVERSATION_NOT_FOUND(3003, "对话不存在"),
    MEMORY_COMPRESS_ERROR(3004, "记忆压缩失败"),

    // 系统内部错误 9xxx
    SYSTEM_ERROR(9999, "系统内部错误"),
    DATABASE_ERROR(9001, "数据库操作失败"),
    NETWORK_ERROR(9002, "网络连接失败");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 根据错误码获取枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
