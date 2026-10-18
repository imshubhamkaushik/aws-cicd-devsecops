package com.catalogix.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan the shared security module for its JWT, rate-limiting, CORS and authentication filter beans.
@SpringBootApplication(scanBasePackages = {"com.catalogix.user", "com.catalogix.security"})
public class UserSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserSvcApplication.class, args);
    }
}
