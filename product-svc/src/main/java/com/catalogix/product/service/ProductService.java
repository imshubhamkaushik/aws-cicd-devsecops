package com.catalogix.product.service;

import com.catalogix.product.dto.CreateProductRequest;
import com.catalogix.product.dto.ProductResponse;
import com.catalogix.product.model.Product;
import com.catalogix.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/* Business Logic must live in the service.
Controller should only handle HTTP concerns.

For production, you would add proper JWT-based auth here
so the userId is verified server-side rather than trusted 
from a plain header.
*/

@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<ProductResponse> listAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    public ProductResponse create(CreateProductRequest req) {
        Product p = new Product();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        return toResponse(repo.save(p));
    }

    public Optional<ProductResponse> findById(long id) {
        return repo.findById(id).map(this::toResponse);
    }

    public boolean deleteById(long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice());
    }
    
}
