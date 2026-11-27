package com.example.petregistryapi.controller;

import com.example.petregistryapi.service.CatalogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.petregistryapi.service.HealthService;

@RestController
public class HtmlController {
    private HealthService healthService;
    private CatalogService catalogService;

    public HtmlController(HealthService healthService,  CatalogService catalogService) {
        this.healthService = healthService;
        this.catalogService = catalogService;
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
    @GetMapping(value = "/view/services", produces = MediaType.TEXT_HTML_VALUE)
    public String viewServices()
    {
        return """
                   <!DOCTYPE html>                                                                                                                                                                                                  
                  <html lang="lt">                                                                                                                                                                                                 
                  <head><meta charset="UTF-8"><title>Pet Registry</title></head>                                                                                                                                                          
                  <body>
                  <h1>%s</h1>                                                                                                                                                                                                                                                                                                                                                                              
                  </body>                                                                                                                                                                                                          
                  </html>                                  
        """.formatted(catalogService.getCatalog());
    }


}
