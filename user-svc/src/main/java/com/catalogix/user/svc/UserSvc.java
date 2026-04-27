package com.catalogix.user.svc;

import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.model.User;
import com.catalogix.user.repository.UserRepository;
import com.catalogix.user.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

/**
 * Business logic for users:
 * - register (hash password)
 * - login (verify password)
 * - listAll (return DTOs)
 * - deleteById
 */

@Service
public class UserSvc {

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;

    public UserSvc(UserRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    // Register a new user, Throws Exception if email already exists
    // @Transactional ensures the findByEmail check and save() are in the same DB transaction, preventing a race where two concurrent requests register the same email.
    @Transactional
    public UserResponse register(CreateUserRequest req) {
        // Check existing email
        if (repo.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword())); // Hash password before saving
        
        User saved = repo.save(user);
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail());
    }

    // Login user: return UserResponse if successful, null if failed
    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest req) {
        return repo.findByEmail(req.getEmail())
                .filter(u -> passwordEncoder.matches(req.getPassword(), u.getPassword()))
                .map(u -> new UserResponse(u.getId(), u.getName(), u.getEmail()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password")); // 3. Throwing exception is usually safer than returning null
    }

    // List all users as DTOs (no passwords)
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return repo.findAll()
                .stream()
                .map(u -> new UserResponse(u.getId(), u.getName(), u.getEmail()))
                .toList();
    }

    // Delete user by id, return true if deleted, false if not found
    // @Transactional ensures the check and delete are in the same DB transaction, preventing a race where the user is deleted between the existsById check and deleteById call.
    @Transactional
    public boolean deleteById(Long id) {
        if (id == null) {
            return false;
        }
        if (!repo.existsById(id)) {
            return false;
        }
        repo.deleteById(id);
        return true;
    }
}
