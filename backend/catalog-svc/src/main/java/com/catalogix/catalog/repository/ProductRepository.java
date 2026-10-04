package com.catalogix.catalog.repository;

import com.catalogix.catalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {

    java.util.List<Product> findByOwnerId(Long ownerId);

    @Query("""
        SELECT p FROM Product p
        WHERE (:search = ''
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:category = '' OR LOWER(p.category) = LOWER(:category))
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND (:seeAll = true OR p.moderationStatus = 'PUBLISHED' OR p.ownerId = :ownerId)
        """)
    Page<Product> search(@Param("search") String search, @Param("category") String category,
                          @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice,
                          @Param("seeAll") boolean seeAll, @Param("ownerId") Long ownerId, Pageable pageable);
}
