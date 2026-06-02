package com.tripagent.config;

import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 *
 * 使用 {@link AgentProperties} 进行类型安全的配置绑定
 */
@Configuration
@EnableConfigurationProperties({AgentProperties.class, RagProperties.class, WeatherProperties.class, AmapProperties.class})
public class ModelConfig {

    private final AgentProperties agentProperties;

    public ModelConfig(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 共享的 DeepSeekApi 实例（HTTP 客户端）
     * 同时配置 RestClient（同步）和 WebClient（响应式）的超时时间为 180 秒
     */
    @Bean
    @Primary
    public DeepSeekApi deepSeekApi(
            @org.springframework.beans.factory.annotation.Value("${spring.ai.deepseek.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl) {
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
                .model(agentProperties.planning().model())
                .temperature((double) agentProperties.planning().temperature())
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
                .model(agentProperties.execution().model())
                .temperature((double) agentProperties.execution().temperature())
                .logprobs(false)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .build();
    }

}
