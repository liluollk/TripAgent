package com.tripagent.service;

import com.tripagent.service.TokenUsageTracker.CallType;
import com.tripagent.service.TokenUsageTracker.CompressionRecord;
import com.tripagent.service.TokenUsageTracker.UsageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageTrackerTest {

    private TokenUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TokenUsageTracker();
    }

    @Test
    void testRecordUsage() {
        // 模拟一次 PLANNING 调用
        tracker.record(CallType.PLANNING, new MockUsage(100, 50, 150));

        var records = tracker.getRecords();
        assertEquals(1, records.size());

        UsageRecord record = records.get(0);
        assertEquals(CallType.PLANNING, record.callType());
        assertEquals(100, record.promptTokens());
        assertEquals(50, record.completionTokens());
        assertEquals(150, record.totalTokens());
    }

    @Test
    void testSummaryByType() {
        // 模拟多次调用
        tracker.record(CallType.PLANNING, new MockUsage(100, 50, 150));
        tracker.record(CallType.PLANNING, new MockUsage(200, 80, 280));
        tracker.record(CallType.EXECUTION, new MockUsage(60, 30, 90));

        Map<String, Object> summary = tracker.getSummary();

        assertEquals(3L, summary.get("totalCalls"));
        assertEquals(360L, summary.get("totalPromptTokens"));
        assertEquals(160L, summary.get("totalCompletionTokens"));
        assertEquals(520L, summary.get("totalTokens"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Long>> byType = (Map<String, Map<String, Long>>) summary.get("byType");
        assertTrue(byType.containsKey("PLANNING"));
        assertTrue(byType.containsKey("EXECUTION"));
        assertEquals(2L, byType.get("PLANNING").get("calls"));
        assertEquals(1L, byType.get("EXECUTION").get("calls"));
    }

    @Test
    void testCompressionRecord() {
        tracker.recordCompression(10, 1000, 800, 200, 150);

        var records = tracker.getCompressionRecords();
        assertEquals(1, records.size());

        CompressionRecord record = records.get(0);
        assertEquals(10, record.originalMessageCount());
        assertEquals(1000, record.originalEstimatedTokens());
        assertEquals(150, record.summaryEstimatedTokens());
        // 压缩率 = 1 - (150 / 1000) = 0.85
        assertEquals(0.85, record.compressionRatio(), 0.01);
    }

    @Test
    void testCompressionSummary() {
        // 模拟两次压缩
        tracker.recordCompression(10, 1000, 800, 200, 150);  // 压缩率 85%
        tracker.recordCompression(8, 800, 600, 150, 100);    // 压缩率 87.5%

        Map<String, Object> summary = tracker.getCompressionSummary();

        assertEquals(2, summary.get("compressionCount"));
        assertEquals(1800, summary.get("totalOriginalTokens"));
        assertEquals(250, summary.get("totalSummaryTokens"));

        // 整体压缩率 = 1 - (250 / 1800) ≈ 86.1%
        double overallRatio = (Double) summary.get("overallRatio");
        assertEquals(86.1, overallRatio, 0.1);
    }

    @Test
    void testEstimateTokens() {
        // 中文文本
        int tokens1 = TokenUsageTracker.estimateTokens("我想去南京玩三天");
        assertTrue(tokens1 > 0 && tokens1 < 10);

        // 英文文本
        int tokens2 = TokenUsageTracker.estimateTokens("I want to visit Nanjing");
        assertTrue(tokens2 > 0 && tokens2 < 10);

        // 混合文本
        int tokens3 = TokenUsageTracker.estimateTokens("南京Nanjing旅游travel");
        assertTrue(tokens3 > 0);

        // 空文本
        assertEquals(0, TokenUsageTracker.estimateTokens(""));
        assertEquals(0, TokenUsageTracker.estimateTokens(null));
    }

    @Test
    void testReset() {
        tracker.record(CallType.PLANNING, new MockUsage(100, 50, 150));
        tracker.recordCompression(10, 1000, 800, 200, 150);

        tracker.reset();

        assertTrue(tracker.getRecords().isEmpty());
        assertTrue(tracker.getCompressionRecords().isEmpty());
        assertEquals(0L, tracker.getSummary().get("totalCalls"));
    }

    /**
     * 模拟 Usage 对象
     */
    private static class MockUsage implements org.springframework.ai.chat.metadata.Usage {
        private final Integer promptTokens;
        private final Integer completionTokens;
        private final Integer totalTokens;

        MockUsage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        @Override
        public Integer getPromptTokens() { return promptTokens; }

        @Override
        public Integer getCompletionTokens() { return completionTokens; }

        @Override
        public Integer getTotalTokens() { return totalTokens; }

        @Override
        public Object getNativeUsage() { return null; }
    }
}
