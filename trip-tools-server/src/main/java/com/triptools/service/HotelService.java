package com.triptools.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tripcommon.model.vo.HotelInfo;
import com.triptools.config.AmapProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图酒店服务
 */
@Service
@Slf4j
public class HotelService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AmapProperties amapProperties;

    public HotelService(RestTemplate restTemplate, ObjectMapper objectMapper, AmapProperties amapProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.amapProperties = amapProperties;
    }

    /**
     * 搜索城市中的酒店
     *
     * 缓存策略：缓存 30 分钟
     */
    @Cacheable(value = "hotel", key = "#city", unless = "#result.isEmpty()")
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<HotelInfo> searchByCity(String city) {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String encodedType = URLEncoder.encode("酒店", StandardCharsets.UTF_8);
            String url = "https://restapi.amap.com/v5/place/text?key=" + amapProperties.apiKey() +
                    "&region=" + encodedCity + "&types=" + encodedType + "&show_fields=business";

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<HotelInfo> hotels = new ArrayList<>();

            if (root.has("pois")) {
                JsonNode poisArray = root.get("pois");
                if (poisArray.isArray()) {
                    for (JsonNode poi : poisArray) {
                        double price = 0;
                        if (poi.has("business") && poi.get("business").has("cost")) {
                            try {
                                price = Double.parseDouble(poi.get("business").get("cost").asText());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        Double rating = null;
                        if (poi.has("business") && poi.get("business").has("rating")) {
                            try {
                                rating = Double.parseDouble(poi.get("business").get("rating").asText());
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        HotelInfo hotelInfo = HotelInfo.builder()
                                .id(poi.has("id") ? poi.get("id").asText() : null)
                                .name(poi.has("name") ? poi.get("name").asText() : "未知")
                                .city(city)
                                .address(poi.has("address") ? poi.get("address").asText() : "未知")
                                .price(price)
                                .rating(rating)
                                .type("酒店")
                                .phone(poi.has("business") && poi.get("business").has("tel") ? poi.get("business").get("tel").asText() : "")
                                .remark("数据来源：高德地图")
                                .build();
                        hotels.add(hotelInfo);
                    }
                }
            }

            return hotels;
        } catch (Exception e) {
            log.error("搜索酒店失败", e);
            return new ArrayList<>();
        }
    }
}
