package com.catalogix.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Include the shared security module in component scanning because these
// services use its JWT, rate-limiting, CORS, and authentication filter beans.
@SpringBootApplication(scanBasePackages = {"com.catalogix.notification", "com.catalogix.security"})
public class NotificationSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationSvcApplication.class, args);
    }
}
