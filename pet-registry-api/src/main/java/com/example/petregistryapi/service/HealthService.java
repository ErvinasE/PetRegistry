package com.example.petregistryapi.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final JdbcTemplate jdbc;

    public HealthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public int ownerCount()
    {
        return jdbc.queryForObject("SELECT COUNT(*) FROM USERS WHERE role = 'pet_owner'", int.class);
    }


}
