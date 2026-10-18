package com.catalogix.catalog.config;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;

/**
 * Serializes sample-data seeding across catalog-svc replicas using a PostgreSQL session advisory
 * lock. The lock lives on a dedicated connection while ProductSvc uses normal short transactions,
 * so the application does not hold a database transaction open around inventory HTTP calls.
 */
@Component
public class ProductSeedLock {

    private static final long LOCK_KEY = 0x434154414C4F4758L; // "CATALOGX"
    private final JdbcTemplate jdbcTemplate;

    public ProductSeedLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void runLocked(Runnable work) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
                lock.setLong(1, LOCK_KEY);
                lock.execute();
            }
            try {
                work.run();
            } finally {
                try (PreparedStatement unlock = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    unlock.setLong(1, LOCK_KEY);
                    unlock.execute();
                }
            }
            return null;
        });
    }
}
