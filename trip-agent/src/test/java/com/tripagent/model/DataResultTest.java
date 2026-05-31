package com.tripagent.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataResult 测试")
class DataResultTest {

    @Test
    @DisplayName("of() 创建真实数据")
    void ofCreatesRealData() {
        DataResult<String> result = DataResult.of("hello");

        assertEquals(DataResult.DataSource.REAL, result.getSource());
        assertEquals("hello", result.getData());
        assertNull(result.getMessage());
        assertTrue(result.isReal());
        assertFalse(result.isDegraded());
        assertFalse(result.isFailed());
        assertTrue(result.hasData());
    }

    @Test
    @DisplayName("degraded() 创建降级数据")
    void degradedCreatesDegradedData() {
        DataResult<String> result = DataResult.degraded("fallback", "API 超时");

        assertEquals(DataResult.DataSource.DEGRADED, result.getSource());
        assertEquals("fallback", result.getData());
        assertEquals("API 超时", result.getMessage());
        assertFalse(result.isReal());
        assertTrue(result.isDegraded());
        assertFalse(result.isFailed());
        assertTrue(result.hasData());
    }

    @Test
    @DisplayName("failed() 创建失败数据")
    void failedCreatesFailedData() {
        DataResult<String> result = DataResult.failed("网络异常");

        assertEquals(DataResult.DataSource.FAILED, result.getSource());
        assertNull(result.getData());
        assertEquals("网络异常", result.getMessage());
        assertFalse(result.isReal());
        assertFalse(result.isDegraded());
        assertTrue(result.isFailed());
        assertFalse(result.hasData());
    }

    @Test
    @DisplayName("DataResult 包装 null 数据")
    void wrapsNullData() {
        DataResult<String> result = DataResult.of(null);

        assertTrue(result.isReal());
        assertFalse(result.hasData());
    }

    @Test
    @DisplayName("DataResult 包装复杂对象")
    void wrapsComplexObject() {
        record Person(String name, int age) {}
        DataResult<Person> result = DataResult.of(new Person("张三", 25));

        assertTrue(result.isReal());
        assertEquals("张三", result.getData().name());
        assertEquals(25, result.getData().age());
    }
}
