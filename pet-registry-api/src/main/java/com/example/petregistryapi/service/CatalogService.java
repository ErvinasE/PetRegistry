package com.example.petregistryapi.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;


@Service
public class CatalogService {
    private final JdbcTemplate jdbc;

    public CatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public List<Map<String, Object>> getCatalog() {
        return jdbc.queryForList("""
                SELECT s.title, s.price, c.name AS company
                FROM services s
                JOIN companies c ON c.id = s.company_id
                """);
    }

}
