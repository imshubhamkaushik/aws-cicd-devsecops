package com.catalogix.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.user.svc.UserSvc;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.mockito.Mockito.when;

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private UserSvc svc;

    @Autowired
    private MockMvc mvc;

    // POST /users/register tests
    @Test
    void registerReturnsCreated() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("Password1");

        when(svc.register(any()))
                .thenReturn(new UserResponse(1L, "John", "john@example.com"));

        mvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("John"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("Password1");

        when(svc.register(any())).thenThrow(new IllegalArgumentException("Email already registered"));

        mvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // POST /users/login tests
    @Test
    void loginReturnsOkWithUserData() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("Password1");

        when(svc.login(any()))
                .thenReturn(new UserResponse(1L, "John", "john@example.com"));

        mvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void loginFailureReturns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("x@x.com");
        req.setPassword("wrongpass");

        when(svc.login(any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // GET /users tests

    @Test
    void getAllReturnsListOfUsers() throws Exception {
        when(svc.listAll()).thenReturn(List.of(
                new UserResponse(1L, "Alice", "alice@example.com"),
                new UserResponse(2L, "Bob",   "bob@example.com")
        ));

        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Bob"));
    }

    @Test
    void getAllReturnsEmptyListWhenNoUsers() throws Exception {
        when(svc.listAll()).thenReturn(List.of());

        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // DELETE /users/{id} tests

    @Test
    void deleteReturnsNoContent() throws Exception {
        when(svc.deleteById(1L)).thenReturn(true);

        mvc.perform(delete("/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturnsNotFoundWhenUserMissing() throws Exception {
        when(svc.deleteById(99L)).thenReturn(false);

        mvc.perform(delete("/users/99"))
                .andExpect(status().isNotFound());
    }
}
