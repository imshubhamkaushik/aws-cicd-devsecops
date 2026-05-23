package com.catalogix.user.svc;

import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.user.model.User;
import com.catalogix.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserSvcTest {

    @Mock
    private UserRepository repo;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private UserSvc svc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // Register tests
    @Test
    @SuppressWarnings("null")
    void registerSucceeds() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Name");
        req.setEmail("name@example.com");
        req.setPassword("Password1");

        when(repo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(encoder.encode(req.getPassword())).thenReturn("hashed");
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse resp = svc.register(req);
        assertNotNull(resp);
        assertEquals(1L, resp.getId());
        assertEquals("Name", resp.getName());
        verify(repo).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void registerDuplicateEmailThrows() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("A");
        req.setEmail("a@a.com");
        req.setPassword("Password1");

        when(repo.findByEmail(req.getEmail())).thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class, () -> svc.register(req));
        verify(repo, never()).save(any());
    }

    // Login tests
    @Test
    void loginSucceeds() {
        User u = new User();
        u.setId(1L);
        u.setEmail("x@x.com");
        u.setPassword("hashed");
        when(repo.findByEmail("x@x.com")).thenReturn(Optional.of(u));
        when(encoder.matches("Password1", "hashed")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail("x@x.com");
        req.setPassword("Password1");

        UserResponse resp = svc.login(req);
        assertNotNull(resp);
        
        long expectedId = Objects.requireNonNull(u.getId(),    "user ID must not be null");
        long actualId   = Objects.requireNonNull(resp.getId(), "response ID must not be null");
        assertEquals(expectedId, actualId);
    }

    @Test
    void loginFailsWithBadPasswordThrowsUnauthorized() {
        User u = new User();
        u.setEmail("x@x.com");
        u.setPassword("hashed");
        when(repo.findByEmail("x@x.com")).thenReturn(Optional.of(u));
        when(encoder.matches("wrongpass", "hashed")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setEmail("x@x.com");
        req.setPassword("wrongpass");

        assertThrows(UnauthorizedException.class, () -> svc.login(req));
    }

    @Test
    void loginFailsWhenEmailNotFoundThrowsUnauthorized() {
        when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("anything");

        assertThrows(UnauthorizedException.class, () -> svc.login(req));
    }

    // listAll tests

    @Test
    void listAllReturnsMappedDTOs() {
        User u1 = new User(); u1.setId(1L); u1.setName("Alice"); u1.setEmail("alice@x.com");
        User u2 = new User(); u2.setId(2L); u2.setName("Bob");   u2.setEmail("bob@x.com");
        when(repo.findAll()).thenReturn(List.of(u1, u2));

        List<UserResponse> result = svc.listAll();
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getName());
        assertEquals("Bob",   result.get(1).getName());
    }

    @Test
    void listAllReturnsEmptyListWhenNoUsers() {
        when(repo.findAll()).thenReturn(List.of());
        assertTrue(svc.listAll().isEmpty());
    }

    // deleteById tests

    @Test
    void deleteByIdReturnsTrueWhenUserExists() {
        when(repo.existsById(1L)).thenReturn(true);
        assertTrue(svc.deleteById(1L));
        verify(repo).deleteById(1L);
    }

    @Test
    void deleteByIdReturnsFalseWhenUserNotFound() {
        when(repo.existsById(99L)).thenReturn(false);
        assertFalse(svc.deleteById(99L));
        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void deleteByIdReturnsFalseForNullId() {
        assertFalse(svc.deleteById(null));
        verify(repo, never()).existsById(anyLong());
        verify(repo, never()).deleteById(anyLong());
    }
}

