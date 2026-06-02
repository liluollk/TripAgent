package com.tripagent.controller;

import com.tripagent.service.TokenUsageTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Token 使用量指标端点
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics API", description = "Token 使用量与压缩效果指标")
public class MetricsController {

    private final TokenUsageTracker tokenUsageTracker;

    @GetMapping("/tokens")
    @Operation(summary = "Token 使用汇总", description = "返回所有 LLM 调用的 token 使用统计")
    public Map<String, Object> getTokenUsage() {
        return tokenUsageTracker.getSummary();
    }

    @GetMapping("/tokens/compression")
    @Operation(summary = "压缩效果详情", description = "返回记忆压缩前后的 token 对比")
    public Map<String, Object> getCompressionMetrics() {
        return tokenUsageTracker.getCompressionSummary();
    }

    @GetMapping("/tokens/records")
    @Operation(summary = "调用记录明细", description = "返回每次 LLM 调用的详细 token 使用记录")
    public Object getRecords() {
        return tokenUsageTracker.getRecords();
    }
}
