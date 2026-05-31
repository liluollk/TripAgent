package com.tripagent.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * 用于表示业务逻辑中的异常情况，如参数错误、业务规则违反等
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final ErrorCode errorCode;

    /**
     * 构造函数
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 构造函数（带自定义消息）
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数（带原因）
     *
     * @param errorCode 错误码枚举
     * @param cause     异常原因
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数（带自定义消息和原因）
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @param cause     异常原因
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return String.format("BusinessException{code=%d, message='%s'}",
                errorCode.getCode(), errorCode.getMessage());
    }
}
