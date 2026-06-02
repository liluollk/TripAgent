package com.tripagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 使用量追踪器
 * 记录每次 LLM 调用的 token 消耗，支持按调用类型分类统计
 */
@Slf4j
@Component
public class TokenUsageTracker {

    /**
     * 调用类型枚举
     */
    public enum CallType {
        PLANNING,       // 规划阶段
        EXECUTION,      // 执行阶段
        COMPRESSION,    // 记忆压缩
        LONG_TERM_MEMORY // 长期记忆提取
    }

    /**
     * 单次调用记录
     */
    public record UsageRecord(
            CallType callType,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long timestamp
    ) {}

    /**
     * 压缩效果记录
     */
    public record CompressionRecord(
            int originalMessageCount,
            int originalEstimatedTokens,
            int compressedPromptTokens,
            int compressedCompletionTokens,
            int summaryEstimatedTokens,
            double compressionRatio,
            long timestamp
    ) {}

    // 所有调用记录
    private final List<UsageRecord> records = new CopyOnWriteArrayList<>();

    /**
     * 最大记录数（防止内存无限增长）
     */
    private static final int MAX_RECORDS = 10_000;

    // 按类型汇总的 prompt token 总量
    private final Map<CallType, AtomicLong> promptTokensByType = new ConcurrentHashMap<>();
    private final Map<CallType, AtomicLong> completionTokensByType = new ConcurrentHashMap<>();
    private final Map<CallType, AtomicLong> callCountByType = new ConcurrentHashMap<>();

    // 压缩效果记录
    private final List<CompressionRecord> compressionRecords = new CopyOnWriteArrayList<>();

    /**
     * 记录一次 LLM 调用的 token 使用
     */
    public void record(CallType callType, Usage usage) {
        int prompt = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int total = usage.getTotalTokens() != null ? usage.getTotalTokens() : prompt + completion;

        UsageRecord record = new UsageRecord(callType, prompt, completion, total, System.currentTimeMillis());
        records.add(record);

        promptTokensByType.computeIfAbsent(callType, k -> new AtomicLong()).addAndGet(prompt);
        completionTokensByType.computeIfAbsent(callType, k -> new AtomicLong()).addAndGet(completion);
        callCountByType.computeIfAbsent(callType, k -> new AtomicLong()).incrementAndGet();

        log.debug("Token usage recorded: type={}, prompt={}, completion={}, total={}",
                callType, prompt, completion, total);
    }

    /**
     * 记录压缩效果
     * @param originalMessageCount 原始消息数量
     * @param originalEstimatedTokens 原始消息估算 token 数
     * @param compressedPromptTokens 压缩调用的 prompt token
     * @param compressedCompletionTokens 压缩调用的 completion token（摘要）
     * @param summaryEstimatedTokens 摘要估算 token 数
     */
    public void recordCompression(int originalMessageCount, int originalEstimatedTokens,
                                  int compressedPromptTokens, int compressedCompletionTokens,
                                  int summaryEstimatedTokens) {
        double ratio = originalEstimatedTokens > 0
                ? 1.0 - ((double) summaryEstimatedTokens / originalEstimatedTokens)
                : 0.0;

        CompressionRecord record = new CompressionRecord(
                originalMessageCount, originalEstimatedTokens,
                compressedPromptTokens, compressedCompletionTokens,
                summaryEstimatedTokens, ratio, System.currentTimeMillis()
        );
        compressionRecords.add(record);

        log.info("压缩效果: 原始{}条消息≈{}tokens → 摘要≈{}tokens, 压缩率={}%",
                originalMessageCount, originalEstimatedTokens, summaryEstimatedTokens,
                Math.round(ratio * 1000.0) / 10.0);
    }

    /**
     * 获取所有调用记录
     */
    public List<UsageRecord> getRecords() {
        return List.copyOf(records);
    }

    /**
     * 获取压缩记录
     */
    public List<CompressionRecord> getCompressionRecords() {
        return List.copyOf(compressionRecords);
    }

    /**
     * 获取汇总数据
     */
    public Map<String, Object> getSummary() {
        long totalCalls = records.size();
        long totalPromptTokens = records.stream().mapToLong(UsageRecord::promptTokens).sum();
        long totalCompletionTokens = records.stream().mapToLong(UsageRecord::completionTokens).sum();
        long totalTokens = records.stream().mapToLong(UsageRecord::totalTokens).sum();

        Map<String, Map<String, Long>> byType = new java.util.LinkedHashMap<>();
        for (CallType type : CallType.values()) {
            long count = callCountByType.containsKey(type) ? callCountByType.get(type).get() : 0;
            long prompt = promptTokensByType.containsKey(type) ? promptTokensByType.get(type).get() : 0;
            long completion = completionTokensByType.containsKey(type) ? completionTokensByType.get(type).get() : 0;
            if (count > 0) {
                byType.put(type.name(), Map.of(
                        "calls", count,
                        "promptTokens", prompt,
                        "completionTokens", completion,
                        "totalTokens", prompt + completion
                ));
            }
        }

        return Map.of(
                "totalCalls", totalCalls,
                "totalPromptTokens", totalPromptTokens,
                "totalCompletionTokens", totalCompletionTokens,
                "totalTokens", totalTokens,
                "byType", byType
        );
    }

    /**
     * 获取压缩效果汇总
     */
    public Map<String, Object> getCompressionSummary() {
        if (compressionRecords.isEmpty()) {
            return Map.of("message", "暂无压缩记录");
        }

        double avgRatio = compressionRecords.stream()
                .mapToDouble(CompressionRecord::compressionRatio)
                .average().orElse(0.0);

        int totalOriginalTokens = compressionRecords.stream()
                .mapToInt(CompressionRecord::originalEstimatedTokens).sum();
        int totalSummaryTokens = compressionRecords.stream()
                .mapToInt(CompressionRecord::summaryEstimatedTokens).sum();

        return Map.of(
                "compressionCount", compressionRecords.size(),
                "averageCompressionRatio", Math.round(avgRatio * 1000.0) / 10.0,  // 保留1位小数
                "totalOriginalTokens", totalOriginalTokens,
                "totalSummaryTokens", totalSummaryTokens,
                "overallRatio", totalOriginalTokens > 0
                        ? Math.round((1.0 - (double) totalSummaryTokens / totalOriginalTokens) * 1000.0) / 10.0
                        : 0.0,
                "details", compressionRecords.stream().map(r -> Map.of(
                        "originalMessageCount", r.originalMessageCount(),
                        "originalEstimatedTokens", r.originalEstimatedTokens(),
                        "summaryEstimatedTokens", r.summaryEstimatedTokens(),
                        "compressionRatio", Math.round(r.compressionRatio() * 1000.0) / 10.0
                )).toList()
        );
    }

    /**
     * 定期清理过旧的调用记录（每小时执行一次）
     * 只保留最近的 MAX_RECORDS 条记录
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanupOldRecords() {
        if (records.size() > MAX_RECORDS) {
            int toRemove = records.size() - MAX_RECORDS;
            // CopyOnWriteArrayList 的 subList 创建快照，然后批量替换
            List<UsageRecord> keep = List.copyOf(records.subList(toRemove, records.size()));
            records.clear();
            records.addAll(keep);
            log.info("清理了 {} 条旧的 Token 使用记录，当前剩余 {} 条", toRemove, records.size());
        }
    }

    /**
     * 重置所有数据（测试用）
     */
    public void reset() {
        records.clear();
        compressionRecords.clear();
        promptTokensByType.clear();
        completionTokensByType.clear();
        callCountByType.clear();
    }

    /**
     * 估算文本的 token 数（中文约 1.5 字符 = 1 token）
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 统计中文字符数和非中文字符数
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        // 中文 1.5 字符 ≈ 1 token，英文 4 字符 ≈ 1 token
        return (int) Math.ceil(chineseChars / 1.5 + otherChars / 4.0);
    }
}
