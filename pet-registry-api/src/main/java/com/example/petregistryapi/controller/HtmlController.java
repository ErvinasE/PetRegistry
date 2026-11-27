package com.example.petregistryapi.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.petregistryapi.service.HealthService;

@RestController
public class HtmlController {
    private HealthService healthService;

    public HtmlController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String hello()
    {
        return """                                                                                                                                                                                                               
                  <!DOCTYPE html>                                                                                                                                                                                                  
                  <html lang="lt">                                                                                                                                                                                                 
                  <head><meta charset="UTF-8"><title>Pet Registry</title></head>                                                                                                                                                          
                  <body>                                                                                                                                                                                                           
                    <h1>Pet Registry</h1>
                    <p>Status: UP</p>
                    <p>Šeimininkai : %d </p>                                                                                                                                                                                             
                    <a href="/api/health">Health check</a>                                                                                                                                                                         
                  </body>                                                                                                                                                                                                          
                  </html>                                                                                                                                                                                                          
                  """.formatted(healthService.ownerCount());
    }

}
