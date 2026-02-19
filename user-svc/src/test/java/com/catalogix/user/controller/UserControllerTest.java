package com.catalogix.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.svc.UserSvc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.mockito.Mockito.when;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockitoBean
    UserSvc svc;

    @Test
    @SuppressWarnings("null")
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
                .andExpect(status().isCreated());
    }
}
