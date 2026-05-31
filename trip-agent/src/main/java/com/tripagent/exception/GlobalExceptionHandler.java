package com.tripagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 统一处理所有异常，返回标准格式的错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());

        Map<String, Object> response = Map.of(
                "success", false,
                "errorMessage", getUserFriendlyMessage(e.getErrorCode())
        );

        // 根据错误码设置 HTTP 状态码
        HttpStatus status = determineHttpStatus(e.getErrorCode());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("参数验证失败: {}", message);

        Map<String, Object> response = Map.of(
                "success", false,
                "errorMessage", "参数验证失败: " + message
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());

        Map<String, Object> response = Map.of(
                "success", false,
                "errorMessage", "请求参数无效，请检查输入"
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);

        Map<String, Object> response = Map.of(
                "success", false,
                "errorMessage", "系统内部错误，请稍后重试"
        );

        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * 根据错误码确定 HTTP 状态码
     */
    private HttpStatus determineHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, USER_NOT_FOUND, CONVERSATION_NOT_FOUND, MEMORY_COMPRESS_ERROR ->
                    HttpStatus.BAD_REQUEST;
            case WEATHER_API_ERROR, POI_API_ERROR, HOTEL_API_ERROR, MCP_TOOL_ERROR ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case AI_SERVICE_ERROR, AI_RESPONSE_PARSE_ERROR, AI_REQUEST_TIMEOUT ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case SYSTEM_ERROR, DATABASE_ERROR, NETWORK_ERROR ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 获取用户友好的错误信息（不暴露内部实现细节）
     */
    private String getUserFriendlyMessage(ErrorCode errorCode) {
        return switch (errorCode) {
            case AI_SERVICE_ERROR -> "AI服务暂时不可用，请稍后重试";
            case AI_RESPONSE_PARSE_ERROR -> "AI响应解析失败，请稍后重试";
            case AI_REQUEST_TIMEOUT -> "AI请求超时，请稍后重试";
            case WEATHER_API_ERROR -> "天气查询失败，请稍后重试";
            case POI_API_ERROR -> "景点查询失败，请稍后重试";
            case HOTEL_API_ERROR -> "酒店查询失败，请稍后重试";
            case MCP_TOOL_ERROR -> "工具调用失败，请稍后重试";
            case INVALID_REQUEST -> "请求参数无效，请检查输入";
            case USER_NOT_FOUND -> "用户不存在";
            case CONVERSATION_NOT_FOUND -> "对话不存在";
            case MEMORY_COMPRESS_ERROR -> "记忆处理失败，请稍后重试";
            case DATABASE_ERROR -> "数据处理失败，请稍后重试";
            case NETWORK_ERROR -> "网络连接失败，请检查网络";
            case SYSTEM_ERROR -> "系统内部错误，请稍后重试";
        };
    }
}
