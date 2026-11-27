package com.example.petregistryapi.controller;

import com.example.petregistryapi.service.CatalogService;
import com.example.petregistryapi.service.HealthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@RestController
public class HtmlController {
    private final HealthService healthService;
    private final CatalogService catalogService;

    public HtmlController(HealthService healthService, CatalogService catalogService) {
        this.healthService = healthService;
        this.catalogService = catalogService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String hello() {
        return """
                <!DOCTYPE html>
                <html lang="lt">
                <head><meta charset="UTF-8"><title>Pet Registry</title></head>
                <body>
                  <h1>Pet Registry</h1>
                  <p>Status: UP</p>
                  <p>Šeimininkai: %d</p>
                  <p><a href="/api/health">Health check</a></p>
                  <p><a href="/view/services">Paslaugos</a></p>
                </body>
                </html>
                """.formatted(healthService.ownerCount());
    }

    @GetMapping(value = "/view/services", produces = MediaType.TEXT_HTML_VALUE)
    public String viewServices() {
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> row : catalogService.getCatalog()) {
            rows.append("<tr>")
                    .append(cell(row.get("title")))
                    .append(cell(row.get("price")))
                    .append(cell(row.get("company")))
                    .append("</tr>\n");
        }

        return """
                <!DOCTYPE html>
                <html lang="lt">
                <head><meta charset="UTF-8"><title>Paslaugos</title></head>
                <body>
                  <h1>Paslaugos</h1>
                  <table>
                    <tr><th>Pavadinimas</th><th>Kaina</th><th>Įmonė</th></tr>
                    %s
                  </table>
                </body>
                </html>
                """.formatted(rows);
    }

    // One table cell. Escaping stops database text from being read as HTML.
    private String cell(Object value) {
        return "<td>" + HtmlUtils.htmlEscape(String.valueOf(value), "UTF-8") + "</td>";
    }
}
