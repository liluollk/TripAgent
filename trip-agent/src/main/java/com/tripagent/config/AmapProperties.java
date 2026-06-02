package com.tripagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 高德地图 API 配置
 */
@ConfigurationProperties(prefix = "trip.amap")
public record AmapProperties(
        String apiKey
) {}
