package com.catalogix.user.svc;

import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.model.User;
import com.catalogix.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

        var resp = svc.register(req);
        assertNotNull(resp);
        assertEquals(1L, resp.getId());
        verify(repo, times(1)).save(any());
    }

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

        var resp = svc.login(req);
        assertNotNull(resp);
        assertEquals(u.getId(), resp.getId());
    }

    @Test
    void registerDuplicateEmailThrows() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("A");
        req.setEmail("a@a.com");
        req.setPassword("Password1");

        when(repo.findByEmail(req.getEmail())).thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class, () -> svc.register(req));
    }
}
