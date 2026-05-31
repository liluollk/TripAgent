# Trip Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a travel planning agent with multi-agent orchestration, dual-model routing, tool calling, and memory system using Spring AI 1.1.7 and DeepSeek API.

**Architecture:** Supervisor Agent pattern with specialized agents (Requirement, POI, Budget) sharing a TripContext. Planning uses deepseek-v4-pro, execution uses deepseek-v4-flash. Short-term memory via JDBC+PostgreSQL, long-term memory via Spring Data JPA.

**Tech Stack:** Java 21, Spring Boot 3.5.14, Spring AI 1.1.7, PostgreSQL, Knife4j

---

## File Structure

```
trip-agent/
├── pom.xml
├── src/main/java/com/tripagent/
│   ├── TripAgentApplication.java
│   ├── config/
│   │   ├── ModelConfig.java
│   │   └── ChatMemoryConfig.java
│   ├── model/
│   │   ├── dto/
│   │   │   ├── TripRequest.java
│   │   │   ├── TripResponse.java
│   │   │   └── ChatMessage.java
│   │   ├── entity/
│   │   │   └── UserProfile.java
│   │   └── vo/
│   │       ├── WeatherInfo.java
│   │       ├── PoiInfo.java
│   │       └── HotelInfo.java
│   ├── repository/
│   │   └── UserProfileRepository.java
│   ├── service/
│   │   ├── WeatherService.java
│   │   ├── PoiService.java
│   │   ├── HotelService.java
│   │   ├── ChatMemoryService.java
│   │   ├── LongTermMemoryService.java
│   │   └── TripPlanningService.java
│   ├── tools/
│   │   └── TripTools.java
│   ├── agent/
│   │   ├── SupervisorAgent.java
│   │   ├── RequirementAgent.java
│   │   ├── PoiAgent.java
│   │   └── BudgetAgent.java
│   └── controller/
│       └── TripController.java
├── src/main/resources/
│   ├── application.yml
│   ├── data/
│   │   └── hotels.json
│   └── db/
│       └── schema.sql
└── src/test/java/com/tripagent/
    └── TripAgentApplicationTests.java
```

---

## Phase 1: Project Skeleton & Configuration

### Task 1.1: Create Maven Project

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.14</version>
        <relativePath/>
    </parent>

    <groupId>com.tripagent</groupId>
    <artifactId>trip-agent</artifactId>
    <version>1.0.0</version>
    <name>Trip Agent</name>
    <description>Travel Planning Agent with Spring AI</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.1.7</spring-ai.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Boot JDBC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Spring AI DeepSeek -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-deepseek</artifactId>
        </dependency>

        <!-- Spring AI MCP Server -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>

        <!-- Knife4j -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>4.5.0</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
</project>
```

- [ ] **Step 2: Verify pom.xml**

Run: `mvn dependency:tree`
Expected: Dependencies resolved successfully

---

### Task 1.2: Create Application Entry Point

**Files:**
- Create: `src/main/java/com/tripagent/TripAgentApplication.java`
- Create: `src/main/resources/application.yml`

- [ ] **Step 1: Create main application class**

```java
package com.tripagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TripAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(TripAgentApplication.class, args);
    }
}
```

- [ ] **Step 2: Create application.yml**

```yaml
spring:
  application:
    name: trip-agent

  # DeepSeek Configuration
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-v4-flash
          temperature: 0.3

  # PostgreSQL Configuration
  datasource:
    url: jdbc:postgresql://localhost:5432/trip_agent
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  # JPA Configuration
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

  # JDBC Configuration for Chat Memory
  sql:
    init:
      mode: always

# Server Configuration
server:
  port: 8080

# Knife4j Configuration
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

# Custom Configuration
trip:
  agent:
    planning:
      model: deepseek-v4-pro
      temperature: 0.7
    execution:
      model: deepseek-v4-flash
      temperature: 0.3
    memory:
      window-size: 20
      compress-threshold: 20
      keep-recent: 10
```

- [ ] **Step 3: Create database**

Run: `createdb trip_agent` (or create via pgAdmin)
Expected: Database created

- [ ] **Step 4: Verify application starts**

Run: `mvn spring-boot:run`
Expected: Application starts (will fail on missing API key, that's OK)

---

## Phase 2: Model Configuration & Basic Chat

### Task 2.1: Configure Dual Model Router

**Files:**
- Create: `src/main/java/com/tripagent/config/ModelConfig.java`

- [ ] **Step 1: Create ModelConfig**

```java
package com.tripagent.config;

import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
     * Planning model - DeepSeek V4 Pro with higher temperature for creative planning
     */
    @Bean("planningChatModel")
    public DeepSeekChatModel planningChatModel() {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .withModel(planningModel)
                .withTemperature(planningTemperature)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * Execution model - DeepSeek V4 Flash with lower temperature for precise execution
     */
    @Bean("executionChatModel")
    @Primary
    public DeepSeekChatModel executionChatModel() {
        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .withModel(executionModel)
                .withTemperature(executionTemperature)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

### Task 2.2: Create DTOs and VOs

**Files:**
- Create: `src/main/java/com/tripagent/model/dto/ChatMessage.java`
- Create: `src/main/java/com/tripagent/model/dto/TripRequest.java`
- Create: `src/main/java/com/tripagent/model/dto/TripResponse.java`
- Create: `src/main/java/com/tripagent/model/vo/WeatherInfo.java`
- Create: `src/main/java/com/tripagent/model/vo/PoiInfo.java`
- Create: `src/main/java/com/tripagent/model/vo/HotelInfo.java`

- [ ] **Step 1: Create ChatMessage DTO**

```java
package com.tripagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    private String role;  // user, assistant, system
    private String content;
}
```

- [ ] **Step 2: Create TripRequest DTO**

```java
package com.tripagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TripRequest {
    private String message;
    private String conversationId;
}
```

- [ ] **Step 3: Create TripResponse DTO**

```java
package com.tripagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TripResponse {
    private String reply;
    private String conversationId;
    private boolean success;
    private String errorMessage;
}
```

- [ ] **Step 4: Create WeatherInfo VO**

```java
package com.tripagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeatherInfo {
    private String city;
    private String weather;
    private String temperature;
    private String humidity;
    private String windDirection;
    private String windPower;
    private String reportTime;
}
```

- [ ] **Step 5: Create PoiInfo VO**

```java
package com.tripagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PoiInfo {
    private String id;
    private String name;
    private String type;
    private String address;
    private String city;
    private double longitude;
    private double latitude;
    private double rating;
    private String businessHours;
}
```

- [ ] **Step 6: Create HotelInfo VO**

```java
package com.tripagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelInfo {
    private String id;
    private String name;
    private String city;
    private String address;
    private double price;
    private double rating;
    private String type;
    private String facilities;
}
```

- [ ] **Step 7: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

## Phase 3: Tool Calling Implementation

### Task 3.1: Create Weather Service

**Files:**
- Create: `src/main/java/com/tripagent/service/WeatherService.java`

- [ ] **Step 1: Create WeatherService**

```java
package com.tripagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.model.vo.WeatherInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WeatherService {

    @Value("${trip.weather.api-key}")
    private String apiKey;

    private static final String BASE_URL = "https://devapi.qweather.com/v7/weather/now";
    private static final String GEO_URL = "https://geoapi.qweather.com/v2/city/lookup";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get current weather for a city
     */
    public WeatherInfo getWeather(String city) {
        try {
            // Step 1: Get city ID
            String locationId = getCityId(city);
            if (locationId == null) {
                return new WeatherInfo(city, "未知", "未知", "未知", "未知", "未知", "未知");
            }

            // Step 2: Get weather
            String url = BASE_URL + "?location=" + locationId + "&key=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("code") && root.get("code").asText().equals("200")) {
                JsonNode now = root.get("now");
                return new WeatherInfo(
                        city,
                        now.get("text").asText(),
                        now.get("temp").asText() + "°C",
                        now.get("humidity").asText() + "%",
                        now.get("windDir").asText(),
                        now.get("windScale").asText() + "级",
                        now.get("obsTime").asText()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new WeatherInfo(city, "获取失败", "未知", "未知", "未知", "未知", "未知");
    }

    /**
     * Get city ID from GeoAPI
     */
    private String getCityId(String city) {
        try {
            String url = GEO_URL + "?location=" + city + "&key=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("code") && root.get("code").asText().equals("200")) {
                JsonNode location = root.get("location");
                if (location != null && location.size() > 0) {
                    return location.get(0).get("id").asText();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
```

- [ ] **Step 2: Add weather API key to application.yml**

Add to `application.yml`:
```yaml
trip:
  weather:
    api-key: ${QWEATHER_API_KEY}
```

---

### Task 3.2: Create POI Service

**Files:**
- Create: `src/main/java/com/tripagent/service/PoiService.java`

- [ ] **Step 1: Create PoiService**

```java
package com.tripagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.model.vo.PoiInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class PoiService {

    @Value("${trip.amap.api-key}")
    private String apiKey;

    private static final String BASE_URL = "https://restapi.amap.com/v5/place/text";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Search POI by keyword
     */
    public List<PoiInfo> searchPoi(String city, String keyword) {
        List<PoiInfo> results = new ArrayList<>();
        try {
            String url = BASE_URL + "?key=" + apiKey
                    + "&keywords=" + keyword
                    + "&region=" + city
                    + "&page_size=10";

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("status") && root.get("status").asText().equals("1")) {
                JsonNode pois = root.get("pois");
                if (pois != null) {
                    for (JsonNode poi : pois) {
                        PoiInfo info = new PoiInfo();
                        info.setId(poi.get("id").asText());
                        info.setName(poi.get("name").asText());
                        info.setType(poi.get("type").asText());
                        info.setAddress(poi.get("address").asText());
                        info.setCity(poi.get("cityname").asText());

                        // Parse location
                        String location = poi.get("location").asText();
                        String[] parts = location.split(",");
                        info.setLongitude(Double.parseDouble(parts[0]));
                        info.setLatitude(Double.parseDouble(parts[1]));

                        // Rating
                        if (poi.has("biz_ext") && poi.get("biz_ext").has("rating")) {
                            String rating = poi.get("biz_ext").get("rating").asText();
                            info.setRating(rating.isEmpty() ? 0.0 : Double.parseDouble(rating));
                        }

                        // Business hours
                        if (poi.has("business_hours")) {
                            info.setBusinessHours(poi.get("business_hours").asText());
                        }

                        results.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Search POI by type
     */
    public List<PoiInfo> searchPoiByType(String city, String typeCode) {
        List<PoiInfo> results = new ArrayList<>();
        try {
            String url = BASE_URL + "?key=" + apiKey
                    + "&types=" + typeCode
                    + "&region=" + city
                    + "&page_size=10";

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("status") && root.get("status").asText().equals("1")) {
                JsonNode pois = root.get("pois");
                if (pois != null) {
                    for (JsonNode poi : pois) {
                        PoiInfo info = new PoiInfo();
                        info.setId(poi.get("id").asText());
                        info.setName(poi.get("name").asText());
                        info.setType(poi.get("type").asText());
                        info.setAddress(poi.get("address").asText());
                        info.setCity(poi.get("cityname").asText());

                        String location = poi.get("location").asText();
                        String[] parts = location.split(",");
                        info.setLongitude(Double.parseDouble(parts[0]));
                        info.setLatitude(Double.parseDouble(parts[1]));

                        results.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}
```

- [ ] **Step 2: Add Amap API key to application.yml**

Add to `application.yml`:
```yaml
trip:
  amap:
    api-key: ${AMAP_API_KEY}
```

---

### Task 3.3: Create Hotel Service with Static Data

**Files:**
- Create: `src/main/resources/data/hotels.json`
- Create: `src/main/java/com/tripagent/service/HotelService.java`

- [ ] **Step 1: Create hotels.json**

```json
{
  "hotels": [
    {
      "id": "H001",
      "name": "杭州西湖国宾馆",
      "city": "杭州",
      "address": "杭州市西湖区杨公堤18号",
      "price": 1280,
      "rating": 4.8,
      "type": "豪华型",
      "facilities": "免费WiFi,游泳池,健身房,餐厅,会议室"
    },
    {
      "id": "H002",
      "name": "杭州西溪湿地公园亚朵酒店",
      "city": "杭州",
      "address": "杭州市西湖区文二西路706号",
      "price": 458,
      "rating": 4.5,
      "type": "舒适型",
      "facilities": "免费WiFi,餐厅,停车场"
    },
    {
      "id": "H003",
      "name": "杭州西湖宋城景区亚朵酒店",
      "city": "杭州",
      "address": "杭州市西湖区之江路148号",
      "price": 398,
      "rating": 4.3,
      "type": "舒适型",
      "facilities": "免费WiFi,餐厅,停车场"
    },
    {
      "id": "H004",
      "name": "杭州河坊街青年旅舍",
      "city": "杭州",
      "address": "杭州市上城区河坊街53号",
      "price": 128,
      "rating": 4.0,
      "type": "经济型",
      "facilities": "免费WiFi,公共厨房"
    },
    {
      "id": "H005",
      "name": "北京希尔顿酒店",
      "city": "北京",
      "address": "北京市朝阳区东三环北路东方路1号",
      "price": 1580,
      "rating": 4.7,
      "type": "豪华型",
      "facilities": "免费WiFi,游泳池,健身房,餐厅,会议室,SPA"
    },
    {
      "id": "H006",
      "name": "北京王府井亚朵酒店",
      "city": "北京",
      "address": "北京市东城区王府井大街200号",
      "price": 528,
      "rating": 4.4,
      "type": "舒适型",
      "facilities": "免费WiFi,餐厅,健身房"
    },
    {
      "id": "H007",
      "name": "上海外滩华尔道夫酒店",
      "city": "上海",
      "address": "上海市黄浦区中山东一路2号",
      "price": 2680,
      "rating": 4.9,
      "type": "豪华型",
      "facilities": "免费WiFi,游泳池,健身房,餐厅,会议室,SPA,酒吧"
    },
    {
      "id": "H008",
      "name": "上海南京路亚朵酒店",
      "city": "上海",
      "address": "上海市黄浦区南京东路800号",
      "price": 488,
      "rating": 4.3,
      "type": "舒适型",
      "facilities": "免费WiFi,餐厅"
    },
    {
      "id": "H009",
      "name": "成都春熙路亚朵酒店",
      "city": "成都",
      "address": "成都市锦江区春熙路步行街",
      "price": 388,
      "rating": 4.4,
      "type": "舒适型",
      "facilities": "免费WiFi,餐厅,停车场"
    },
    {
      "id": "H010",
      "name": "成都宽窄巷子青旅",
      "city": "成都",
      "address": "成都市青羊区宽窄巷子",
      "price": 98,
      "rating": 4.1,
      "type": "经济型",
      "facilities": "免费WiFi,公共厨房"
    }
  ]
}
```

- [ ] **Step 2: Create HotelService**

```java
package com.tripagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripagent.model.vo.HotelInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HotelService {

    private List<HotelInfo> allHotels = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("data/hotels.json");
        allHotels = objectMapper.readValue(
                resource.getInputStream(),
                new TypeReference<List<HotelInfo>>() {}
        );
    }

    /**
     * Search hotels by city
     */
    public List<HotelInfo> searchByCity(String city) {
        return allHotels.stream()
                .filter(h -> h.getCity().equals(city))
                .collect(Collectors.toList());
    }

    /**
     * Search hotels by city and max price
     */
    public List<HotelInfo> searchByCityAndPrice(String city, double maxPrice) {
        return allHotels.stream()
                .filter(h -> h.getCity().equals(city) && h.getPrice() <= maxPrice)
                .collect(Collectors.toList());
    }

    /**
     * Search hotels by city, max price, and min rating
     */
    public List<HotelInfo> search(String city, double maxPrice, double minRating) {
        return allHotels.stream()
                .filter(h -> h.getCity().equals(city)
                        && h.getPrice() <= maxPrice
                        && h.getRating() >= minRating)
                .collect(Collectors.toList());
    }

    /**
     * Get hotel by ID
     */
    public HotelInfo getById(String id) {
        return allHotels.stream()
                .filter(h -> h.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

### Task 3.4: Create Trip Tools

**Files:**
- Create: `src/main/java/com/tripagent/tools/TripTools.java`

- [ ] **Step 1: Create TripTools**

```java
package com.tripagent.tools;

import com.tripagent.model.vo.HotelInfo;
import com.tripagent.model.vo.PoiInfo;
import com.tripagent.model.vo.WeatherInfo;
import com.tripagent.service.HotelService;
import com.tripagent.service.PoiService;
import com.tripagent.service.WeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TripTools {

    private final WeatherService weatherService;
    private final PoiService poiService;
    private final HotelService hotelService;

    public TripTools(WeatherService weatherService, PoiService poiService, HotelService hotelService) {
        this.weatherService = weatherService;
        this.poiService = poiService;
        this.hotelService = hotelService;
    }

    @Tool(description = "获取指定城市的当前天气信息，包括温度、天气状况、湿度、风向等")
    public WeatherInfo getWeather(
            @ToolParam(description = "城市名称，如：杭州、北京、上海") String city
    ) {
        return weatherService.getWeather(city);
    }

    @Tool(description = "搜索城市中的兴趣点(POI)，如景点、餐厅、商场等。返回名称、地址、评分等信息")
    public List<PoiInfo> searchPoi(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "搜索关键词，如：西湖、故宫、外滩") String keyword
    ) {
        return poiService.searchPoi(city, keyword);
    }

    @Tool(description = "搜索酒店信息，包括价格、评分、位置等。可按城市、最高价格、最低评分筛选")
    public List<HotelInfo> searchHotel(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "最高价格（元/晚），如：500") double maxPrice,
            @ToolParam(description = "最低评分（1-5），如：4.0") double minRating
    ) {
        return hotelService.search(city, maxPrice, minRating);
    }

    @Tool(description = "搜索城市中的所有酒店，不筛选价格和评分")
    public List<HotelInfo> searchAllHotels(
            @ToolParam(description = "城市名称") String city
    ) {
        return hotelService.searchByCity(city);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

## Phase 4: Memory System

### Task 4.1: Configure Chat Memory with JDBC

**Files:**
- Create: `src/main/java/com/tripagent/config/ChatMemoryConfig.java`
- Create: `src/main/resources/db/schema.sql`

- [ ] **Step 1: Create ChatMemoryConfig**

```java
package com.tripagent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatMemoryConfig {

    @Value("${trip.agent.memory.window-size:20}")
    private int windowSize;

    /**
     * Chat Memory Repository using JDBC + PostgreSQL
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .build();
    }

    /**
     * Chat Memory with sliding window
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(windowSize)
                .build();
    }
}
```

- [ ] **Step 2: Create schema.sql (optional, Spring AI auto-creates)**

```sql
-- Spring AI will auto-create the table
-- This file is for reference only

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(255) NOT NULL,
    message_index INT NOT NULL,
    message_content TEXT,
    message_type VARCHAR(50),
    PRIMARY KEY (conversation_id, message_index)
);
```

- [ ] **Step 3: Update application.yml for JDBC**

Update `application.yml`:
```yaml
spring:
  ai:
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: always
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

### Task 4.2: Create Chat Memory Service

**Files:**
- Create: `src/main/java/com/tripagent/service/ChatMemoryService.java`

- [ ] **Step 1: Create ChatMemoryService**

```java
package com.tripagent.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMemoryService {

    private final ChatMemory chatMemory;

    public ChatMemoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * Add user message to memory
     */
    public void addUserMessage(String conversationId, String content) {
        chatMemory.add(conversationId, new UserMessage(content));
    }

    /**
     * Add assistant message to memory
     */
    public void addAssistantMessage(String conversationId, String content) {
        chatMemory.add(conversationId, new AssistantMessage(content));
    }

    /**
     * Get conversation history
     */
    public List<Message> getMessages(String conversationId) {
        return chatMemory.get(conversationId);
    }

    /**
     * Clear conversation history
     */
    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

### Task 4.3: Create Long-Term Memory

**Files:**
- Create: `src/main/java/com/tripagent/model/entity/UserProfile.java`
- Create: `src/main/java/com/tripagent/repository/UserProfileRepository.java`
- Create: `src/main/java/com/tripagent/service/LongTermMemoryService.java`

- [ ] **Step 1: Create UserProfile entity**

```java
package com.tripagent.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profile")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;  // JSON format

    @Column(name = "visited_cities", columnDefinition = "TEXT")
    private String visitedCities;  // Comma-separated

    @Column(name = "budget_preference")
    private Double budgetPreference;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        lastActiveAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create UserProfileRepository**

```java
package com.tripagent.repository;

import com.tripagent.model.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
}
```

- [ ] **Step 3: Create LongTermMemoryService**

```java
package com.tripagent.service;

import com.tripagent.model.entity.UserProfile;
import com.tripagent.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LongTermMemoryService {

    private final UserProfileRepository userProfileRepository;

    public LongTermMemoryService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * Get or create user profile
     */
    public UserProfile getOrCreateProfile(String userId) {
        return userProfileRepository.findById(userId)
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setUserId(userId);
                    profile.setVisitedCities("");
                    return userProfileRepository.save(profile);
                });
    }

    /**
     * Update user preferences
     */
    public void updatePreferences(String userId, String preferences) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setPreferences(preferences);
        userProfileRepository.save(profile);
    }

    /**
     * Add visited city
     */
    public void addVisitedCity(String userId, String city) {
        UserProfile profile = getOrCreateProfile(userId);
        String cities = profile.getVisitedCities();
        if (cities == null || cities.isEmpty()) {
            profile.setVisitedCities(city);
        } else if (!cities.contains(city)) {
            profile.setVisitedCities(cities + "," + city);
        }
        userProfileRepository.save(profile);
    }

    /**
     * Update budget preference
     */
    public void updateBudgetPreference(String userId, double budget) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setBudgetPreference(budget);
        userProfileRepository.save(profile);
    }

    /**
     * Get memory context for planning
     */
    public String getMemoryContext(String userId) {
        UserProfile profile = getOrCreateProfile(userId);
        StringBuilder context = new StringBuilder();

        if (profile.getPreferences() != null && !profile.getPreferences().isEmpty()) {
            context.append("用户偏好: ").append(profile.getPreferences()).append("\n");
        }

        if (profile.getVisitedCities() != null && !profile.getVisitedCities().isEmpty()) {
            context.append("历史访问城市: ").append(profile.getVisitedCities()).append("\n");
        }

        if (profile.getBudgetPreference() != null) {
            context.append("预算偏好: ").append(profile.getBudgetPreference()).append("元\n");
        }

        return context.length() > 0 ? context.toString() : "新用户，无历史记录";
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

## Phase 5: Agent System

### Task 5.1: Create Agent DTOs

**Files:**
- Create: `src/main/java/com/tripagent/model/dto/TripRequest.java` (update)
- Create: `src/main/java/com/tripagent/model/dto/TripResponse.java` (update)

- [ ] **Step 1: Update TripRequest**

```java
package com.tripagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TripRequest {
    private String message;
    private String userId;
    private String conversationId;
}
```

- [ ] **Step 2: Update TripResponse**

```java
package com.tripagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TripResponse {
    private String reply;
    private String conversationId;
    private String userId;
    private boolean success;
    private String errorMessage;
}
```

---

### Task 5.2: Create Supervisor Agent

**Files:**
- Create: `src/main/java/com/tripagent/agent/SupervisorAgent.java`

- [ ] **Step 1: Create SupervisorAgent**

```java
package com.tripagent.agent;

import com.tripagent.model.dto.TripResponse;
import com.tripagent.service.ChatMemoryService;
import com.tripagent.service.LongTermMemoryService;
import com.tripagent.service.TripPlanningService;
import com.tripagent.tools.TripTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SupervisorAgent {

    private final ChatModel planningModel;
    private final ChatModel executionModel;
    private final ChatMemoryService chatMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final TripTools tripTools;
    private final TripPlanningService tripPlanningService;

    public SupervisorAgent(
            @Qualifier("planningChatModel") ChatModel planningModel,
            @Qualifier("executionChatModel") ChatModel executionModel,
            ChatMemoryService chatMemoryService,
            LongTermMemoryService longTermMemoryService,
            TripTools tripTools,
            TripPlanningService tripPlanningService) {
        this.planningModel = planningModel;
        this.executionModel = executionModel;
        this.chatMemoryService = chatMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.tripTools = tripTools;
        this.tripPlanningService = tripPlanningService;
    }

    /**
     * Handle user request
     */
    public TripResponse handleRequest(String userId, String message) {
        try {
            String conversationId = "trip:" + userId;

            // Get user memory context
            String memoryContext = longTermMemoryService.getMemoryContext(userId);

            // Build system prompt
            String systemPrompt = buildSystemPrompt(memoryContext);

            // Get conversation history
            var history = chatMemoryService.getMessages(conversationId);

            // Build messages
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(history);
            messages.add(new UserMessage(message));

            // Use execution model with tools
            ChatClient chatClient = ChatClient.builder(executionModel)
                    .defaultTools(tripTools)
                    .build();

            // Execute
            String response = chatClient.prompt()
                    .messages(messages.toArray(new org.springframework.ai.chat.messages.Message[0]))
                    .call()
                    .content();

            // Save to memory
            chatMemoryService.addUserMessage(conversationId, message);
            chatMemoryService.addAssistantMessage(conversationId, response);

            // Update long-term memory
            updateLongTermMemory(userId, message, response);

            return new TripResponse(response, conversationId, userId, true, null);

        } catch (Exception e) {
            e.printStackTrace();
            return new TripResponse(null, null, userId, false, e.getMessage());
        }
    }

    /**
     * Build system prompt with memory context
     */
    private String buildSystemPrompt(String memoryContext) {
        return """
                你是一个专业的旅行规划助手。你可以帮助用户：
                1. 查询城市天气信息
                2. 搜索景点、餐厅等兴趣点
                3. 搜索酒店信息
                4. 规划旅行行程
                
                用户历史信息：
                %s
                
                请根据用户的需求，使用提供的工具来获取信息，并给出专业的旅行建议。
                回答要友好、详细、有条理。
                """.formatted(memoryContext);
    }

    /**
     * Update long-term memory based on conversation
     */
    private void updateLongTermMemory(String userId, String userMessage, String response) {
        // Simple heuristic: extract city names
        String[] cities = {"杭州", "北京", "上海", "成都", "广州", "深圳", "西安", "南京", "苏州", "重庆"};
        for (String city : cities) {
            if (userMessage.contains(city) || response.contains(city)) {
                longTermMemoryService.addVisitedCity(userId, city);
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

### Task 5.3: Create Trip Planning Service

**Files:**
- Create: `src/main/java/com/tripagent/service/TripPlanningService.java`

- [ ] **Step 1: Create TripPlanningService**

```java
package com.tripagent.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripPlanningService {

    private final ChatModel planningModel;
    private final ChatModel executionModel;
    private final LongTermMemoryService longTermMemoryService;

    public TripPlanningService(
            @Qualifier("planningChatModel") ChatModel planningModel,
            @Qualifier("executionChatModel") ChatModel executionModel,
            LongTermMemoryService longTermMemoryService) {
        this.planningModel = planningModel;
        this.executionModel = executionModel;
        this.longTermMemoryService = longTermMemoryService;
    }

    /**
     * Generate travel plan using planning model
     */
    public String generatePlan(String userId, String requirement) {
        String memoryContext = longTermMemoryService.getMemoryContext(userId);

        String systemPrompt = """
                你是一个旅行规划专家。根据用户需求生成详细的旅行计划。
                
                用户历史信息：
                %s
                
                请生成详细的旅行计划，包括：
                1. 行程安排（每天的活动）
                2. 景点推荐
                3. 住宿建议
                4. 预算估算
                5. 注意事项
                
                请用清晰的格式输出。
                """.formatted(memoryContext);

        var messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(requirement)
        );

        return planningModel.call(new Prompt(messages))
                .getResult()
                .getOutput()
                .getContent();
    }

    /**
     * Analyze requirement using planning model
     */
    public String analyzeRequirement(String requirement) {
        String systemPrompt = """
                分析用户的旅行需求，提取以下信息：
                1. 目的地
                2. 天数
                3. 预算
                4. 人数
                5. 特殊要求
                
                以 JSON 格式返回。
                """;

        var messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(requirement)
        );

        return planningModel.call(new Prompt(messages))
                .getResult()
                .getOutput()
                .getContent();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

## Phase 6: REST API

### Task 6.1: Create Trip Controller

**Files:**
- Create: `src/main/java/com/tripagent/controller/TripController.java`

- [ ] **Step 1: Create TripController**

```java
package com.tripagent.controller;

import com.tripagent.agent.SupervisorAgent;
import com.tripagent.model.dto.TripRequest;
import com.tripagent.model.dto.TripResponse;
import com.tripagent.service.ChatMemoryService;
import com.tripagent.service.LongTermMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trip")
@Tag(name = "Trip Agent", description = "旅行规划助手 API")
public class TripController {

    private final SupervisorAgent supervisorAgent;
    private final ChatMemoryService chatMemoryService;
    private final LongTermMemoryService longTermMemoryService;

    public TripController(SupervisorAgent supervisorAgent,
                          ChatMemoryService chatMemoryService,
                          LongTermMemoryService longTermMemoryService) {
        this.supervisorAgent = supervisorAgent;
        this.chatMemoryService = chatMemoryService;
        this.longTermMemoryService = longTermMemoryService;
    }

    @PostMapping("/chat")
    @Operation(summary = "与旅行助手对话", description = "发送消息给旅行助手，获取回复")
    public ResponseEntity<TripResponse> chat(@RequestBody TripRequest request) {
        // Generate conversation ID if not provided
        String userId = request.getUserId() != null ? request.getUserId() : "default";
        String conversationId = request.getConversationId() != null
                ? request.getConversationId()
                : "trip:" + userId;

        TripResponse response = supervisorAgent.handleRequest(userId, request.getMessage());
        response.setConversationId(conversationId);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/chat/simple")
    @Operation(summary = "简单对话接口", description = "只传消息，默认用户ID")
    public ResponseEntity<String> simpleChat(@RequestParam String message,
                                             @RequestParam(defaultValue = "default") String userId) {
        TripResponse response = supervisorAgent.handleRequest(userId, message);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response.getReply());
        } else {
            return ResponseEntity.internalServerError().body("Error: " + response.getErrorMessage());
        }
    }

    @DeleteMapping("/history/{userId}")
    @Operation(summary = "清除对话历史", description = "清除指定用户的对话历史")
    public ResponseEntity<String> clearHistory(@PathVariable String userId) {
        String conversationId = "trip:" + userId;
        chatMemoryService.clear(conversationId);
        return ResponseEntity.ok("History cleared for user: " + userId);
    }

    @GetMapping("/memory/{userId}")
    @Operation(summary = "获取用户记忆", description = "获取用户的长期记忆信息")
    public ResponseEntity<String> getMemory(@PathVariable String userId) {
        String memory = longTermMemoryService.getMemoryContext(userId);
        return ResponseEntity.ok(memory);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

---

## Phase 7: Testing & Integration

### Task 7.1: Create Test Configuration

**Files:**
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: Create test configuration**

```yaml
spring:
  ai:
    deepseek:
      api-key: test-key
      base-url: https://api.deepseek.com
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: embedded

  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop

trip:
  weather:
    api-key: test-key
  amap:
    api-key: test-key
  agent:
    planning:
      model: deepseek-v4-pro
      temperature: 0.7
    execution:
      model: deepseek-v4-flash
      temperature: 0.3
```

---

### Task 7.2: Create Integration Test

**Files:**
- Create: `src/test/java/com/tripagent/TripAgentIntegrationTest.java`

- [ ] **Step 1: Create integration test**

```java
package com.tripagent;

import com.tripagent.model.dto.TripRequest;
import com.tripagent.model.dto.TripResponse;
import com.tripagent.controller.TripController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TripAgentIntegrationTest {

    @Autowired
    private TripController tripController;

    @Test
    void contextLoads() {
        assertNotNull(tripController);
    }

    // Note: Real API tests require valid API keys
    // Run with: mvn test -Dtrip.weather.api-key=YOUR_KEY -Dtrip.amap.api-key=YOUR_KEY
}
```

---

## Execution Checklist

- [ ] Phase 1: Project skeleton compiles
- [ ] Phase 2: Model configuration works
- [ ] Phase 3: Tool calling works (weather, POI, hotel)
- [ ] Phase 4: Memory system works (JDBC + PostgreSQL)
- [ ] Phase 5: Agent system works
- [ ] Phase 6: REST API works
- [ ] Phase 7: Integration tests pass

---

## Environment Variables Required

```bash
export DEEPSEEK_API_KEY=your_deepseek_api_key
export QWEATHER_API_KEY=your_qweather_api_key
export AMAP_API_KEY=your_amap_api_key
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

---

## Run Commands

```bash
# Start PostgreSQL
docker run -d --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=trip_agent postgres:16

# Run application
mvn spring-boot:run

# Test API
curl -X POST http://localhost:8080/api/trip/chat/simple?message=杭州天气怎么样
```
