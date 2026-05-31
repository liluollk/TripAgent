package com.triptools.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tripcommon.model.vo.HotelInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图酒店服务
 */
@Service
@Slf4j
public class HotelService {

    @Value("${trip.amap.api-key}")
    private String amapApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HotelService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
            String url = "https://restapi.amap.com/v5/place/text?key=" + amapApiKey +
                    "&region=" + city + "&types=酒店&show_fields=business";

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
                                .name(poi.has("name") ? poi.get("name").asText() : "未知")
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
