package com.catalogix.product.controller;

import com.catalogix.product.dto.CreateProductRequest;
import com.catalogix.product.dto.ProductResponse;
import com.catalogix.product.svc.ProductSvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// FIX: now mocks ProductService, not ProductRepository.
// Controller no longer has direct access to the repo.
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean
    ProductSvc svc;

    @Test
    void listReturnsOk() throws Exception {
        when(svc.listAll()).thenReturn(Collections.emptyList());
        mvc.perform(get("/products").header("X-USER-ID", 123L))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("null")
    void createReturnsCreated() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setName("Phone");
        req.setDescription("Nice phone");
        req.setPrice(new BigDecimal("100.00"));

        when(svc.create(any(CreateProductRequest.class)))
                .thenReturn(new ProductResponse(1L, "Phone", "Nice phone", new BigDecimal("100.00")));

        mvc.perform(post("/products")
                .header("X-USER-ID", 123L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Phone"))
                .andExpect(jsonPath("$.price").value(100.00));
    }

    @Test
    void getOneReturnsNotFoundWhenMissing() throws Exception {
        when(svc.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/products/99").header("X-USER-ID", 123L))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        when(svc.deleteById(1L)).thenReturn(true);
        mvc.perform(delete("/products/1").header("X-USER-ID", 123L))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturnsNotFoundWhenMissing() throws Exception {
        when(svc.deleteById(99L)).thenReturn(false);
        mvc.perform(delete("/products/99").header("X-USER-ID", 123L))
                .andExpect(status().isNotFound());
    }
}