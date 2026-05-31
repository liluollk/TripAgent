package com.tripagent.utils;

import tools.jackson.databind.JsonNode;

/**
 * JSON 工具类
 */
public class JsonUtils {

    private JsonUtils() {
        // 私有构造函数，防止实例化
    }

    // JsonNode 安全取值

    /**
     * 安全获取文本字段，不存在时返回默认值
     */
    public static String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) ? node.get(field).asText(defaultValue) : defaultValue;
    }

    /**
     * 安全获取整数字段，不存在时返回默认值
     */
    public static int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        return node.has(field) ? node.get(field).asInt(defaultValue) : defaultValue;
    }

    /**
     * 安全获取浮点数字段，不存在时返回默认值
     */
    public static double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        return node.has(field) ? node.get(field).asDouble(defaultValue) : defaultValue;
    }

    /**
     * 安全获取布尔字段，不存在时返回默认值
     */
    public static boolean getBooleanOrDefault(JsonNode node, String field, boolean defaultValue) {
        return node.has(field) ? node.get(field).asBoolean() : defaultValue;
    }

    /**
     * 从文本中提取 JSON 字符串
     * 支持从 AI 回复中提取被包裹的 JSON 内容
     *
     * @param text 包含 JSON 的文本
     * @return 提取的 JSON 字符串，如果未找到则返回原文
     */
    public static String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int start = text.indexOf("{");
        if (start < 0) {
            return text;
        }

        // 用括号匹配找到完整的 JSON 对象，正确处理字符串内的花括号
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            // 处理转义字符（仅在字符串内有效）
            if (escape) {
                escape = false;
                continue;
            }

            // 字符串内的反斜杠：下一个字符是转义
            if (inString && c == '\\') {
                escape = true;
                continue;
            }

            // 双引号：切换字符串状态
            if (c == '"') {
                inString = !inString;
                continue;
            }

            // 只在字符串外统计花括号
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }

        // 如果括号不匹配，回退到最后一个 '}'
        int end = text.lastIndexOf("}");
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 从文本中提取 JSON 数组字符串
     *
     * @param text 包含 JSON 数组的文本
     * @return 提取的 JSON 数组字符串，如果未找到则返回原文
     */
    public static String extractJsonArray(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int start = text.indexOf("[");
        if (start < 0) {
            return text;
        }

        // 用括号匹配找到完整的 JSON 数组，正确处理字符串内的方括号
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (inString && c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }

        // 回退到最后一个 ']'
        int end = text.lastIndexOf("]");
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 检查文本是否包含有效的 JSON 对象
     *
     * @param text 要检查的文本
     * @return 是否包含 JSON 对象
     */
    public static boolean containsJson(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("{") && text.contains("}");
    }

    /**
     * 检查文本是否包含有效的 JSON 数组
     *
     * @param text 要检查的文本
     * @return 是否包含 JSON 数组
     */
    public static boolean containsJsonArray(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("[") && text.contains("]");
    }
}
