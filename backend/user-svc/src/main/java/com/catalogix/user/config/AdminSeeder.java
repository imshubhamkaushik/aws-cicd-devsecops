package com.catalogix.user.config;

import com.catalogix.user.model.User;
import com.catalogix.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Guarantees a fresh deployment has a working admin login. Registration only creates
 * customers and only an admin can grant higher roles, so without a seeded admin nobody
 * could ever approve a seller.
 *
 * On startup, if no admin exists, one is created (pre-verified) from SEED_ADMIN_EMAIL /
 * SEED_ADMIN_PASSWORD. Independent of SEED_DATA, which only controls demo catalogue data.
 * - an admin already exists: nothing to do, later password changes never touch it
 * - no admin and no password: logs an ERROR and starts anyway rather than crash-looping
 * - seed email already taken by an ordinary account: logs an ERROR and does not promote it
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final String seedAdminEmail;
    private final String seedAdminPassword;

    public AdminSeeder(
            UserRepository repo,
            PasswordEncoder passwordEncoder,
            @Value("${SEED_ADMIN_EMAIL:admin@catalogix.local}") String seedAdminEmail,
            @Value("${SEED_ADMIN_PASSWORD:}") String seedAdminPassword
    ) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.seedAdminEmail = seedAdminEmail;
        this.seedAdminPassword = seedAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (repo.countByRole("ADMIN") > 0) {
            return; // an admin already exists — nothing to do on this or any later startup
        }
        if (seedAdminPassword == null || seedAdminPassword.length() < MIN_PASSWORD_LENGTH) {
            log.error("NO ADMIN ACCOUNT EXISTS and SEED_ADMIN_PASSWORD is not set (or shorter than {} "
                    + "characters). Nobody will be able to approve sellers or assign roles. Provide "
                    + "SEED_ADMIN_PASSWORD (on AWS: the seed_admin_password key of the app secret) "
                    + "and restart user-svc.", MIN_PASSWORD_LENGTH);
            return;
        }
        if (repo.findByEmail(seedAdminEmail).isPresent()) {
            log.error("NO ADMIN ACCOUNT EXISTS, but {} is already registered as an ordinary account. It "
                    + "is NOT being promoted (it belongs to whoever registered it). Set SEED_ADMIN_EMAIL "
                    + "to an unused address and restart user-svc.", seedAdminEmail);
            return;
        }

        User admin = new User();
        admin.setName("Admin");
        admin.setEmail(seedAdminEmail);
        admin.setPassword(passwordEncoder.encode(seedAdminPassword));
        admin.setRole("ADMIN");
        // Seeded, not self-registered — there's no inbox to click a verification link
        // from, so start it pre-verified rather than stuck behind the "email not
        // verified" banner.
        admin.setVerified(true);
        try {
            repo.save(admin);
            log.info("Created the bootstrap admin account: {}", seedAdminEmail);
        } catch (DataIntegrityViolationException e) {
            // Several replicas start at once on a fresh deployment; another one won the race.
            log.info("Bootstrap admin {} was created by another replica.", seedAdminEmail);
        }
    }
}
