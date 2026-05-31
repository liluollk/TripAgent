package com.tripagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置
 *
 * 用于 ExecutionAgent 的并行 MCP 工具调用（天气/景点/酒店查询）
 * 使用独立线程池，避免阻塞 ForkJoinPool.commonPool
 */
@Configuration
public class AsyncConfig {

    @Bean("mcpTaskExecutor")
    public Executor mcpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mcp-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
