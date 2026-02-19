package com.catalogix.product.controller;

import com.catalogix.product.dto.CreateProductRequest;
import com.catalogix.product.dto.ProductResponse;
import com.catalogix.product.model.Product;
import com.catalogix.product.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository repo;

    public ProductController(ProductRepository repo) {
        this.repo = repo;
    }

    // List products as ProductResponse DTOs
    @GetMapping
    public ResponseEntity<List<ProductResponse>> listAll(
        @RequestHeader("X-USER-ID") Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }

        List<ProductResponse> products = repo.findAll().stream()
                .map(p -> new ProductResponse(
                    p.getId(), 
                    p.getName(), 
                    p.getDescription(), 
                    p.getPrice()))
                .toList();

        return ResponseEntity.ok(products);
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

        Product p = new Product();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        
        Product saved = repo.save(p);
        
        ProductResponse resp = new ProductResponse(
            saved.getId(), 
            saved.getName(), 
            saved.getDescription(), 
            saved.getPrice()
        );

        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(saved.getId())
            .toUri();

        return ResponseEntity.created(location).body(resp);
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

        return repo.findById(id)
            .map(p -> ResponseEntity.ok(
                    new ProductResponse(
                        p.getId(), 
                        p.getName(), 
                        p.getDescription(), 
                        p.getPrice()
                    )))
            .orElse(ResponseEntity.notFound().build());
    }

    // Delete product
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @RequestHeader("X-USER-ID") Long userId,
        @PathVariable("id") long id
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }
        if (!repo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
