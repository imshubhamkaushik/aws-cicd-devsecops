package com.catalogix.product.svc;

import com.catalogix.product.dto.CreateProductRequest;
import com.catalogix.product.dto.ProductResponse;
import com.catalogix.product.model.Product;
import com.catalogix.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/* Business Logic must live in the service.
Controller should only handle HTTP concerns.

For production, you would add proper JWT-based auth here
so the userId is verified server-side rather than trusted 
from a plain header.
*/

@Service
public class ProductSvc {

    private final ProductRepository repo;

    public ProductSvc(ProductRepository repo) {
        this.repo = repo;
    }

    // Read-only transaction — keeps a consistent snapshot for the full list fetch.
    @Transactional(readOnly = true)
    public List<ProductResponse> listAll() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    // @Transactional ensures the save and any constraint checks are atomic.
    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        Product p = new Product();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        return toResponse(repo.save(p));
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> findById(long id) {
        return repo.findById(id).map(this::toResponse);
    }

    // @Transactional wraps existsById + deleteById atomically so a concurrent delete between the two calls can't produce a spurious EntityNotFoundException.
    @Transactional
    public boolean deleteById(long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice());
    }
    
}
