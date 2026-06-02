package com.tripagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识增强配置
 */
@ConfigurationProperties(prefix = "trip.rag")
public record RagProperties(
        boolean enabled,
        String knowledgeFile
) {}
