package com.triptools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Trip Tools MCP Server 启动类
 * 提供旅行相关的 MCP 工具服务
 */
@SpringBootApplication
public class TripToolsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripToolsServerApplication.class, args);
    }
}
