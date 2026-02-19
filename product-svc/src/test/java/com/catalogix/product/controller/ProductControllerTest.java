package com.catalogix.product.controller;

import com.catalogix.product.controller.ProductController;
import com.catalogix.product.dto.CreateProductRequest;
import com.catalogix.product.model.Product;
import com.catalogix.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockitoBean
    ProductRepository repo;

    @Test
    void listReturnsOk() throws Exception {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        mvc.perform(get("/products")
                .header("X-USER-ID", 123L))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("null")
    void createReturnsCreated() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Phone");
        req.setDescription("Nice phone");
        req.setPrice(100.0);

        Product mockProduct = new Product();
        mockProduct.setId(1L);
        mockProduct.setName(req.getName());
        mockProduct.setDescription(req.getDescription());
        mockProduct.setPrice(req.getPrice());

        // The IDE thinks 'any()' returns null, but Mockito handles it.
        // The suppression above stops the IDE from complaining.
        when(repo.save(any(Product.class))).thenReturn(mockProduct);

        // you may need to mock repo.save if controller relies on it; but in WebMvcTest,
        // normally controller is tested in isolation
        mvc.perform(post("/products")
                .header("X-USER-ID", 123L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Phone"))
                .andExpect(jsonPath("$.description").value("Nice phone"))
                .andExpect(jsonPath("$.price").value(100.0));

    }
}
