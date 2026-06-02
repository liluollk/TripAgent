package com.triptools.config;

import tools.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 应用配置类
 *
 * 使用 Apache HttpClient 5 连接池，提升 HTTP 调用性能
 */
@Configuration
@EnableConfigurationProperties({WeatherProperties.class, AmapProperties.class})
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public HttpClientConnectionManager httpClientConnectionManager() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);              // 最大连接数
        cm.setDefaultMaxPerRoute(20);    // 每个路由（目标主机）最大连接数
        return cm;
    }

    @Bean
    public RestTemplate restTemplate(HttpClientConnectionManager connectionManager) {
        org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .build())
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}
