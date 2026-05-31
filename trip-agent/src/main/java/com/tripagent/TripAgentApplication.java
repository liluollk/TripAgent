package com.tripagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TripAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(TripAgentApplication.class, args);
    }
}
