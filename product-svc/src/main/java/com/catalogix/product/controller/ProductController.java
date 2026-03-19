package com.catalogix.product.controller;

import com.catalogix.product.dto.CreateProductRequest;
import com.catalogix.product.dto.ProductResponse;
import com.catalogix.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// DEV NOTE: X-USER-ID is a dev-only stand-in for auth. For production,
// replace with Spring Security + JWT so identity is verified server-side.

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService svc;

    public ProductController(ProductService svc) {
        this.svc = svc;
    }

    // List products as ProductResponse DTOs
    @GetMapping
    public ResponseEntity<List<ProductResponse>> listAll(
        @RequestHeader("X-USER-ID") Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }

        return ResponseEntity.ok(svc.listAll());
    }
    
    // Create product with validation
    @PostMapping
    public ResponseEntity<ProductResponse> create(
        @RequestHeader("X-USER-ID") Long userId,
        @Valid @RequestBody CreateProductRequest req
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }

        ProductResponse created = svc.create(req);

        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // Get single product
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getOne(
        @RequestHeader("X-USER-ID") Long userId,
        @PathVariable long id
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }

        return svc.findById(id).map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // Delete product
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @RequestHeader("X-USER-ID") Long userId,
        @PathVariable long id
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }
        if (!svc.deleteById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
