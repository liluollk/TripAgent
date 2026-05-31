package com.triptools.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tripcommon.model.vo.PoiInfo;
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
 * 高德地图 POI 服务
 */
@Service
@Slf4j
public class PoiService {

    @Value("${trip.amap.api-key}")
    private String amapApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PoiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索城市中的 POI
     *
     * 缓存策略：缓存 30 分钟
     */
    @Cacheable(value = "poi", key = "#city + ':' + #type", unless = "#result.isEmpty()")
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<PoiInfo> searchPoi(String city, String type) {
        try {
            String url = "https://restapi.amap.com/v5/place/text?key=" + amapApiKey +
                    "&region=" + city + "&types=" + type + "&show_fields=business";

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<PoiInfo> pois = new ArrayList<>();

            if (root.has("pois")) {
                JsonNode poisArray = root.get("pois");
                if (poisArray.isArray()) {
                    for (JsonNode poi : poisArray) {
                        Double longitude = null;
                        Double latitude = null;
                        if (poi.has("location")) {
                            String[] coords = poi.get("location").asText().split(",");
                            if (coords.length == 2) {
                                try {
                                    longitude = Double.parseDouble(coords[0]);
                                    latitude = Double.parseDouble(coords[1]);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }

                        PoiInfo poiInfo = PoiInfo.builder()
                                .name(poi.has("name") ? poi.get("name").asText() : "未知")
                                .address(poi.has("address") ? poi.get("address").asText() : "未知")
                                .type(poi.has("type") ? poi.get("type").asText() : "未知")
                                .longitude(longitude)
                                .latitude(latitude)
                                .phone(poi.has("business") && poi.get("business").has("tel") ? poi.get("business").get("tel").asText() : "")
                                .remark("数据来源：高德地图")
                                .build();
                        pois.add(poiInfo);
                    }
                }
            }

            return pois;
        } catch (Exception e) {
            log.error("搜索POI失败", e);
            return new ArrayList<>();
        }
    }
}
