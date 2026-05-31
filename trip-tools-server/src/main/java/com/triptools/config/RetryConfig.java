package com.triptools.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 重试配置
 * 启用 Spring Retry，支持 @Retryable 注解
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
