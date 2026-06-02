package com.tripagent.service;

import com.tripagent.agent.core.AgentContext;
import com.tripagent.service.TokenUsageTracker.CallType;
import com.tripagent.service.TokenUsageTracker.CompressionRecord;
import com.tripagent.service.TokenUsageTracker.UsageRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压缩效果验证测试（纯单元测试，不依赖 Spring/Mockito）
 */
class MemoryCompressionIntegrationTest {

    @Test
    void testTokenEstimation() {
        String chinese = "我想去南京玩三天，看看中山陵和夫子庙，吃吃鸭血粉丝汤";
        int tokens = TokenUsageTracker.estimateTokens(chinese);
        System.out.println("\n=== Token 估算 ===");
        System.out.println("文本: " + chinese);
        System.out.println("字符数: " + chinese.length());
        System.out.println("估算tokens: " + tokens);
        assertTrue(tokens > 5 && tokens < chinese.length());
    }

    @Test
    void testCompressionSimulation() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        // 模拟原始对话：20条消息，每条约50-100字符
        List<String> originalMessages = List.of(
            "我想去南京玩三天，帮我规划一下",
            "好的！南京是一座历史文化名城，三天时间可以很好地体验这座城市。让我先了解一下你的偏好。",
            "预算大概5000元左右，喜欢自然风光和历史文化",
            "5000元预算在南京玩三天完全够用。南京的景点门票相对便宜，很多公园都是免费的。",
            "不想去太商业化的地方，有什么推荐吗",
            "推荐你去：1. 中山陵 - 中国近代建筑史上的第一陵，免费开放；2. 明孝陵 - 世界文化遗产，神道很美；3. 颐和路 - 民国风情街，很适合散步拍照；4. 老门东 - 比夫子庙更有老南京味道。",
            "好吃的呢？",
            "南京必吃美食：鸭血粉丝汤（回味鸭血粉丝）、盐水鸭（韩复兴）、小笼包（鸡鸣汤包）、牛肉锅贴（李记清真馆）、赤豆元宵（莲湖糕团店）。",
            "住宿有什么建议",
            "建议住在新街口附近，是南京的市中心，地铁1号线和2号线交汇，去哪都方便。预算200-300元/晚可以住到不错的酒店。",
            "交通怎么安排",
            "南京地铁很方便，主要景点都能到。建议买一张金陵通卡，坐地铁公交都方便。从机场到市区坐地铁S1号线，约40分钟。",
            "有没有本地人才知道的好去处",
            "本地人推荐：1. 紫金山天文台 - 可以看到整个南京城；2. 颐和路民国风情街 - 很安静，适合散步；3. 先锋书店 - 全球最美书店之一；4. 玄武湖公园 - 本地人晨练的地方，免费。",
            "听起来不错，帮我生成详细的行程",
            "好的，下面是详细的三日行程安排：第一天：中山陵 → 明孝陵 → 美龄宫 → 老门东晚餐；第二天：总统府 → 颐和路 → 先锋书店 → 夫子庙夜景；第三天：玄武湖公园 → 鸡鸣寺 → 紫金山天文台",
            "第一天和第二天的顺序可以调换吗",
            "当然可以！调换后的行程：第一天：总统府 → 颐和路 → 先锋书店 → 夫子庙夜景；第二天：中山陵 → 明孝陵 → 美龄宫 → 老门东晚餐；第三天：玄武湖公园 → 鸡鸣寺 → 紫金山天文台",
            "好的，就这样定了",
            "好的！行程已确认。祝你在南京玩得愉快！记得穿舒适的鞋子，中山陵和明孝陵需要走不少路。"
        );

        // 计算原始消息总token
        String allOriginal = String.join("\n", originalMessages);
        int originalTokens = TokenUsageTracker.estimateTokens(allOriginal);

        // 模拟LLM压缩后的摘要
        String summary = "【摘要】用户计划南京三日游，预算5000元，喜欢自然风光和历史文化，不想去太商业化的地方。" +
            "已推荐中山陵、明孝陵、老门东等景点，以及鸭血粉丝汤等美食。建议住新街口附近，交通方便。" +
            "行程安排：第一天中山陵明孝陵，第二天总统府颐和路，第三天玄武湖鸡鸣寺。";
        int summaryTokens = TokenUsageTracker.estimateTokens(summary);

        // 记录压缩效果
        tracker.recordCompression(
            originalMessages.size(),
            originalTokens,
            800,  // API返回的prompt tokens
            150,  // API返回的completion tokens
            summaryTokens
        );

        // 记录API调用
        tracker.record(CallType.COMPRESSION, new SimpleUsage(800, 150, 950));

        // 输出结果
        CompressionRecord r = tracker.getCompressionRecords().get(0);
        System.out.println("\n========== 压缩效果验证 ==========");
        System.out.println("原始消息数: " + r.originalMessageCount());
        System.out.println("原始总字符数: " + allOriginal.length());
        System.out.println("原始估算tokens: " + r.originalEstimatedTokens());
        System.out.println("摘要总字符数: " + summary.length());
        System.out.println("摘要估算tokens: " + r.summaryEstimatedTokens());
        System.out.println("压缩率: " + Math.round(r.compressionRatio() * 100) + "%");
        System.out.println("\nAPI实际消耗:");
        System.out.println("  prompt tokens: 800");
        System.out.println("  completion tokens: 150");
        System.out.println("  total tokens: 950");
        System.out.println("=================================\n");

        // 验证
        assertTrue(r.compressionRatio() > 0.5, "压缩率应大于50%");
        assertTrue(r.summaryEstimatedTokens() < r.originalEstimatedTokens());
    }

    @Test
    void testMultipleCompressions() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        // 模拟3轮压缩
        for (int round = 0; round < 3; round++) {
            int originalTokens = 1000 + round * 200;
            int summaryTokens = 150 + round * 20;
            tracker.recordCompression(20, originalTokens, 800, 150, summaryTokens);
            tracker.record(CallType.COMPRESSION, new SimpleUsage(800, 150, 950));
        }

        Map<String, Object> summary = tracker.getCompressionSummary();
        System.out.println("\n========== 多轮压缩汇总 ==========");
        System.out.println("压缩次数: " + summary.get("compressionCount"));
        System.out.println("平均压缩率: " + summary.get("averageCompressionRatio") + "%");
        System.out.println("总原始tokens: " + summary.get("totalOriginalTokens"));
        System.out.println("总摘要tokens: " + summary.get("totalSummaryTokens"));
        System.out.println("整体压缩率: " + summary.get("overallRatio") + "%");
        System.out.println("===================================\n");

        assertEquals(3, summary.get("compressionCount"));
        double overall = (Double) summary.get("overallRatio");
        assertTrue(overall > 50, "整体压缩率应大于50%");
    }

    @Test
    void testTokenUsageSummary() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        // 模拟不同类型的调用
        tracker.record(CallType.PLANNING, new SimpleUsage(500, 200, 700));
        tracker.record(CallType.PLANNING, new SimpleUsage(600, 250, 850));
        tracker.record(CallType.EXECUTION, new SimpleUsage(200, 100, 300));
        tracker.record(CallType.COMPRESSION, new SimpleUsage(800, 150, 950));
        tracker.record(CallType.LONG_TERM_MEMORY, new SimpleUsage(300, 80, 380));

        Map<String, Object> summary = tracker.getSummary();
        System.out.println("\n========== Token使用汇总 ==========");
        System.out.println("总调用次数: " + summary.get("totalCalls"));
        System.out.println("总prompt tokens: " + summary.get("totalPromptTokens"));
        System.out.println("总completion tokens: " + summary.get("totalCompletionTokens"));
        System.out.println("总tokens: " + summary.get("totalTokens"));
        System.out.println("\n按类型:");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Long>> byType = (Map<String, Map<String, Long>>) summary.get("byType");
        byType.forEach((type, data) -> System.out.println("  " + type + ": " + data));
        System.out.println("===================================\n");

        assertEquals(3180L, summary.get("totalTokens"));
        assertTrue(byType.containsKey("PLANNING"));
        assertTrue(byType.containsKey("EXECUTION"));
        assertTrue(byType.containsKey("COMPRESSION"));
        assertTrue(byType.containsKey("LONG_TERM_MEMORY"));
    }

    // 简单的Usage实现
    static class SimpleUsage implements org.springframework.ai.chat.metadata.Usage {
        private final int prompt, completion, total;
        SimpleUsage(int prompt, int completion, int total) {
            this.prompt = prompt; this.completion = completion; this.total = total;
        }
        @Override public Integer getPromptTokens() { return prompt; }
        @Override public Integer getCompletionTokens() { return completion; }
        @Override public Integer getTotalTokens() { return total; }
        @Override public Object getNativeUsage() { return null; }
    }
}
