package com.catalogix.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Include the shared security module in component scanning because these
// services use its JWT, rate-limiting, CORS, and authentication filter beans.
@SpringBootApplication(scanBasePackages = {"com.catalogix.inventory", "com.catalogix.security"})
public class InventorySvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventorySvcApplication.class, args);
    }
}
