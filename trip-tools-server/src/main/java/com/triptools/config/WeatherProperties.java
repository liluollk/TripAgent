package com.triptools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 和风天气 API 配置
 */
@ConfigurationProperties(prefix = "trip.weather")
public record WeatherProperties(
        String apiKey,
        String host,
        String geoHost
) {}
