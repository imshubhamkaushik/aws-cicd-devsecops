package com.catalogix.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Include the shared security module in component scanning because these
// services use its JWT, rate-limiting, CORS, and authentication filter beans.
@SpringBootApplication(scanBasePackages = {"com.catalogix.payment", "com.catalogix.security"})
public class PaymentSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentSvcApplication.class, args);
    }
}
