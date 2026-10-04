package com.catalogix.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Include the shared security module in component scanning because these
// services use its JWT, rate-limiting, CORS, and authentication filter beans.
@SpringBootApplication(scanBasePackages = {"com.catalogix.catalog", "com.catalogix.security"})
public class CatalogSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogSvcApplication.class, args);
    }
}
