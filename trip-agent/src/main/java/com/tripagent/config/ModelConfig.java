package com.tripagent.config;

import com.tripagent.agent.core.ToolRegistry;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 模型配置类
 * 配置 DeepSeek API 客户端和聊天模型
 */
@Configuration
public class ModelConfig {

    @Value("${trip.agent.planning.model:deepseek-v4-pro}")
    private String planningModel;

    @Value("${trip.agent.planning.temperature:0.7}")
    private float planningTemperature;

    @Value("${trip.agent.execution.model:deepseek-v4-flash}")
    private String executionModel;

    @Value("${trip.agent.execution.temperature:0.3}")
    private float executionTemperature;

    @Value("${spring.ai.deepseek.api-key}")
    private String apiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    /**
     * 共享的 DeepSeekApi 实例（HTTP 客户端）
     * 同时配置 RestClient（同步）和 WebClient（响应式）的超时时间为 180 秒
     */
    @Bean
    @Primary
    public DeepSeekApi deepSeekApi() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);
        requestFactory.setReadTimeout(180000);
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(180));
        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        return DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
    }

    /**
     * 规划模型 - DeepSeek V4 Pro，较高温度用于创意规划
     */
    @Bean("planningChatModel")
    public DeepSeekChatModel planningChatModel(DeepSeekApi deepSeekApi) {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(planningModel)
                .temperature((double) planningTemperature)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 执行模型 - DeepSeek V4 Flash，较低温度用于精确执行
     */
    @Bean("executionChatModel")
    public DeepSeekChatModel executionChatModel(DeepSeekApi deepSeekApi) {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(executionModel)
                .temperature((double) executionTemperature)
                .logprobs(false)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 启动时初始化工具注册表
     */
    @Bean
    public CommandLineRunner initToolRegistry(ToolRegistry toolRegistry) {
        return args -> toolRegistry.initDefaultTools();
    }
}
