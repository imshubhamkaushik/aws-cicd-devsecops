package com.catalogix.product.integration;

import com.catalogix.product.model.Product;
import com.catalogix.product.repository.ProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProductRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ALLOWED_ORIGINS", () -> "http://localhost:3000");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProductRepository productRepository;

    @Test
    void contextLoads() {
        assertThat(productRepository).isNotNull();
    }

    // FIX: original only checked count >= 0 — a test that can never fail
    // is not a test. These now actually save, find, and delete data.

    @Test
    void saveAndFindProduct() {
        Product p = new Product("Laptop", "A fast laptop", new BigDecimal("55000.00"));
        Product saved = productRepository.save(p);

        Long savedId = Objects.requireNonNull(saved.getId(), "saved ID must not be null");
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Laptop");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("55000.00"));

        Optional<Product> found = productRepository.findById(savedId);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Laptop");
    }

    @Test
    void listAllProducts() {
        productRepository.deleteAll();
        productRepository.save(new Product("Phone", "A phone", new BigDecimal("15000.00")));
        productRepository.save(new Product("Tablet", "A tablet", new BigDecimal("25000.00")));

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(2);
    }

    @Test
    void deleteProduct() {
        Product p = productRepository.save(
                new Product("Headphones", "Wireless", new BigDecimal("3000.00")));
        Long id = Objects.requireNonNull(p.getId(), "Saved product ID should not be null");
        productRepository.deleteById(id);
        assertThat(productRepository.findById(id)).isEmpty();
    }
}