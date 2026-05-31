package com.triptools.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tripcommon.model.vo.WeatherInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 和风天气 API 服务
 */
@Service
@Slf4j
public class WeatherService {

    @Value("${trip.weather.api-key}")
    private String apiKey;

    @Value("${trip.weather.host}")
    private String host;

    @Value("${trip.weather.geo-host}")
    private String geoHost;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取城市实时天气
     *
     * 重试策略：最多重试 3 次，初始延迟 1 秒，指数退避
     * 缓存策略：缓存 30 分钟
     */
    @Cacheable(value = "weather", key = "#city", unless = "#result.remark.contains('失败')")
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public WeatherInfo getWeather(String city) {
        try {
            // 先获取城市ID
            String locationId = getLocationId(city);
            if (locationId == null) {
                return WeatherInfo.builder()
                        .city(city)
                        .remark("未找到城市：" + city)
                        .build();
            }

            // 获取实时天气
            String url = "https://" + host + "/v7/weather/now?location=" + locationId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-QW-Api-Key", apiKey);
            headers.set("Accept-Encoding", "gzip, deflate");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(
                    url, HttpMethod.GET, entity, byte[].class);

            String response = decompressResponse(responseEntity.getBody());
            JsonNode root = objectMapper.readTree(response);

            if (root.has("code") && "200".equals(root.get("code").asText())) {
                JsonNode now = root.get("now");
                return WeatherInfo.builder()
                        .city(city)
                        .condition(now.get("text").asText())
                        .temperature(now.get("temp").asText() + "°C")
                        .feelsLike(now.get("feelsLike").asText() + "°C")
                        .humidity(now.get("humidity").asText() + "%")
                        .windDir(now.get("windDir").asText())
                        .windSpeed(now.get("windSpeed").asText() + " km/h")
                        .obsTime(now.get("obsTime").asText())
                        .remark("数据来源：和风天气")
                        .build();
            } else {
                return WeatherInfo.builder()
                        .city(city)
                        .remark("获取天气失败：" + root.path("code").asText())
                        .build();
            }
        } catch (Exception e) {
            log.error("获取天气信息失败", e);
            return WeatherInfo.builder()
                    .city(city)
                    .remark("获取天气异常：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 获取城市 LocationID
     */
    private String getLocationId(String city) {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = "https://" + geoHost + "/geo/v2/city/lookup?location=" + encodedCity;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-QW-Api-Key", apiKey);
            headers.set("Accept-Encoding", "gzip, deflate");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(
                    url, HttpMethod.GET, entity, byte[].class);

            String response = decompressResponse(responseEntity.getBody());
            JsonNode root = objectMapper.readTree(response);

            if (root.has("code") && "200".equals(root.get("code").asText())) {
                JsonNode location = root.get("location");
                if (location != null && location.isArray() && !location.isEmpty()) {
                    return location.get(0).get("id").asText();
                }
            }
        } catch (Exception e) {
            log.error("获取城市ID失败", e);
        }
        return null;
    }

    /**
     * 解压 HTTP 响应（自动处理 Gzip）
     */
    private String decompressResponse(byte[] responseBytes) throws Exception {
        if (responseBytes == null) {
            return "";
        }
        // 检查 Gzip 魔数 (0x1f 0x8b)
        if (responseBytes.length >= 2 &&
                responseBytes[0] == (byte) 0x1f && responseBytes[1] == (byte) 0x8b) {
            try (java.util.zip.GZIPInputStream gzipInputStream = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(responseBytes));
                 java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzipInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
                return outputStream.toString(StandardCharsets.UTF_8);
            }
        }
        return new String(responseBytes, StandardCharsets.UTF_8);
    }
}
