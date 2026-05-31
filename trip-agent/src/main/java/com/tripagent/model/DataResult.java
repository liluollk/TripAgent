package com.tripagent.model;

import lombok.Data;

/**
 * 统一数据包装 — 标记数据来源，让用户可判断可信度
 *
 * @param <T> 数据类型
 */
@Data
public class DataResult<T> {

    /**
     * 数据来源
     */
    private final DataSource source;

    /**
     * 实际数据（FAILED 时为 null）
     */
    private final T data;

    /**
     * 降级/失败原因
     */
    private final String message;

    // ========== 工厂方法 ==========

    public static <T> DataResult<T> of(T data) {
        return new DataResult<>(DataSource.REAL, data, null);
    }

    public static <T> DataResult<T> degraded(T data, String reason) {
        return new DataResult<>(DataSource.DEGRADED, data, reason);
    }

    public static <T> DataResult<T> failed(String reason) {
        return new DataResult<>(DataSource.FAILED, null, reason);
    }

    // ========== 便捷判断 ==========

    public boolean isReal() {
        return source == DataSource.REAL;
    }

    public boolean isDegraded() {
        return source == DataSource.DEGRADED;
    }

    public boolean isFailed() {
        return source == DataSource.FAILED;
    }

    public boolean hasData() {
        return data != null;
    }

    /**
     * 数据来源枚举
     */
    public enum DataSource {
        /** 真实 API 数据 */
        REAL,
        /** 降级默认值 */
        DEGRADED,
        /** 查询失败 */
        FAILED
    }
}
