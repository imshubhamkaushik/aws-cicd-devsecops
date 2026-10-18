package com.catalogix.catalog.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Backs the "products" cache (ProductSvc.findById) with in-memory Caffeine. The cache is
 * per-instance; multiple replicas would need a shared cache such as Redis. The short TTL
 * (30s) bounds how stale a price read at checkout can be.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("products");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(30, TimeUnit.SECONDS));
        return manager;
    }
}
