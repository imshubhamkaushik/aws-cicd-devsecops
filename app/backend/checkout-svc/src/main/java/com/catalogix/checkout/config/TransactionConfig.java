package com.catalogix.checkout.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Programmatic transactions for CheckoutSvc's order-placement and payment flows, which call other
 * services between database steps and therefore cannot wrap the whole method in one @Transactional
 * (see "TRANSACTION BOUNDARIES" in CheckoutSvc). Declared explicitly rather than relying on Boot's
 * auto-configuration so the dependency is visible where it is used; Boot's own TransactionTemplate
 * backs off when this bean exists.
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
