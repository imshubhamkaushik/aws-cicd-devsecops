package com.catalogix.user.controller;

import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.svc.UserSvc;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserSvc svc;

    public UserController(UserSvc svc) {
        this.svc = svc;
    }

    // List all users (response without password)
    @GetMapping
    public List<UserResponse> getAll() {
        return svc.listAll();
    }

    // Register endpoint: creates a new user. Validates input, hashes password in service.
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody CreateUserRequest req) {
        // Optionally, the service may throw an exception if email exists — handle via global exception handler
        UserResponse created = svc.register(req);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Login endpoint: validates credentials and returns user info (no password) on success.
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest req) {
        UserResponse user = svc.login(req);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(user);
    }

    //Delete user by id
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        boolean deleted = svc.deleteById(id);
        if (!deleted)
            return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
