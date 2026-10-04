package com.catalogix.user.config;

import com.catalogix.user.model.User;
import com.catalogix.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class AdminSeederTest {

    private static final String EMAIL = "admin@catalogix.local";

    @Mock private UserRepository repo;
    @Mock private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private AdminSeeder seeder(String password) {
        return new AdminSeeder(repo, passwordEncoder, EMAIL, password);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(repo.countByRole("ADMIN")).thenReturn(1L);

        seeder("a-strong-password").run();

        verify(repo, never()).save(any());
        verify(repo, never()).findByEmail(any());
    }

    @Test
    void createsAVerifiedAdminOnAFreshDeployment() {
        when(repo.countByRole("ADMIN")).thenReturn(0L);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-strong-password")).thenReturn("hashed-pw");

        seeder("a-strong-password").run();

        verify(repo).save(argThat(u ->
                u.getEmail().equals(EMAIL)
                        && u.getPassword().equals("hashed-pw")
                        && u.getRole().equals("ADMIN")
                        && u.isVerified()));
    }

    @Test
    void doesNotDependOnSeedDataBeingEnabled() {
        // The first admin is a requirement of every deployment, not demo content, so the
        // seeder does not depend on a SEED_DATA flag.
        when(repo.countByRole("ADMIN")).thenReturn(0L);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("h");

        seeder("another-strong-password").run();

        verify(repo).save(any(User.class));
    }

    @Test
    void startsWithoutAnAdminButDoesNotCreateOneWhenThePasswordIsBlank() {
        when(repo.countByRole("ADMIN")).thenReturn(0L);

        seeder("").run();

        verify(repo, never()).save(any());
    }

    @Test
    void refusesAPasswordThatIsTooShort() {
        when(repo.countByRole("ADMIN")).thenReturn(0L);

        seeder("short").run();

        verify(repo, never()).save(any());
    }

    @Test
    void neverPromotesAnOrdinaryAccountThatHoldsTheSeedEmail() {
        when(repo.countByRole("ADMIN")).thenReturn(0L);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.of(new User()));

        seeder("a-strong-password").run();

        verify(repo, never()).save(any());
    }

    @Test
    void toleratesAnotherReplicaCreatingTheAdminFirst() {
        when(repo.countByRole("ADMIN")).thenReturn(0L);
        when(repo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("h");
        when(repo.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertDoesNotThrow(() -> seeder("a-strong-password").run());
    }
}
