package com.example.petregistryapi.controller;

import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.petregistryapi.service.HealthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP",
                "time", LocalDateTime.now(),
                "owners", healthService.ownerCount());

    }

//    public int count() {
//        return healthService.userCount();
//    }


}
