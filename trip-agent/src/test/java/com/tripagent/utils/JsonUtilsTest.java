package com.tripagent.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonUtils 测试")
class JsonUtilsTest {

    @Nested
    @DisplayName("extractJson")
    class ExtractJson {

        @Test
        @DisplayName("正常 JSON 对象")
        void normalJsonObject() {
            String text = "这是结果: {\"name\": \"杭州\", \"days\": 3}";
            String result = JsonUtils.extractJson(text);
            assertEquals("{\"name\": \"杭州\", \"days\": 3}", result);
        }

        @Test
        @DisplayName("嵌套 JSON 对象")
        void nestedJsonObject() {
            String text = "结果: {\"city\": \"杭州\", \"info\": {\"temp\": \"25°C\"}}";
            String result = JsonUtils.extractJson(text);
            assertEquals("{\"city\": \"杭州\", \"info\": {\"temp\": \"25°C\"}}", result);
        }

        @Test
        @DisplayName("字符串内包含花括号 — 核心修复")
        void bracesInsideString() {
            String text = "结果: {\"desc\": \"价格{500}元\", \"name\": \"杭州\"}";
            String result = JsonUtils.extractJson(text);
            assertEquals("{\"desc\": \"价格{500}元\", \"name\": \"杭州\"}", result);
        }

        @Test
        @DisplayName("字符串内包含转义引号和花括号")
        void escapedQuoteAndBraces() {
            String text = "结果: {\"desc\": \"他说\\\"你好{世界}\\\"\"}";
            String result = JsonUtils.extractJson(text);
            assertEquals("{\"desc\": \"他说\\\"你好{世界}\\\"\"}", result);
        }

        @Test
        @DisplayName("多个 JSON 对象只取第一个")
        void multipleJsonObjects() {
            String text = "{\"a\":1} some text {\"b\":2}";
            String result = JsonUtils.extractJson(text);
            assertEquals("{\"a\":1}", result);
        }

        @Test
        @DisplayName("无 JSON 返回原文")
        void noJsonReturnsOriginal() {
            String text = "没有任何JSON";
            String result = JsonUtils.extractJson(text);
            assertEquals(text, result);
        }

        @Test
        @DisplayName("null 返回 null")
        void nullReturnsNull() {
            assertNull(JsonUtils.extractJson(null));
        }

        @Test
        @DisplayName("空字符串返回空")
        void emptyReturnsEmpty() {
            assertEquals("", JsonUtils.extractJson(""));
        }

        @Test
        @DisplayName("AI 回复包裹的 JSON")
        void aiWrappedJson() {
            String text = """
                    根据分析，以下是结果：
                    ```json
                    {"destination": "杭州", "days": 3, "budget": 5000}
                    ```
                    以上是分析结果。
                    """;
            String result = JsonUtils.extractJson(text);
            assertEquals("{\"destination\": \"杭州\", \"days\": 3, \"budget\": 5000}", result);
        }
    }

    @Nested
    @DisplayName("extractJsonArray")
    class ExtractJsonArray {

        @Test
        @DisplayName("正常 JSON 数组")
        void normalJsonArray() {
            String text = "结果: [{\"name\":\"西湖\"},{\"name\":\"灵隐寺\"}]";
            String result = JsonUtils.extractJsonArray(text);
            assertEquals("[{\"name\":\"西湖\"},{\"name\":\"灵隐寺\"}]", result);
        }

        @Test
        @DisplayName("数组内字符串包含方括号 — 核心修复")
        void bracketsInsideString() {
            String text = "结果: [{\"name\":\"A[1]\"},{\"name\":\"B\"}]";
            String result = JsonUtils.extractJsonArray(text);
            assertEquals("[{\"name\":\"A[1]\"},{\"name\":\"B\"}]", result);
        }
    }
}
