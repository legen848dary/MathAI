package com.insoftu.mathai.admin.config;

import com.insoftu.mathai.admin.model.AdminUser;
import com.insoftu.mathai.admin.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = System.getenv("ADMIN_EMAIL");
        this.adminPassword = System.getenv("ADMIN_PASSWORD");
    }

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("ADMIN_EMAIL not set — skipping admin seeding.");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL is set but ADMIN_PASSWORD is empty — skipping admin seeding.");
            return;
        }

        if (adminUserRepository.findByEmail(adminEmail).isPresent()) {
            log.info("Admin user '{}' already exists — skipping seeding.", adminEmail);
            return;
        }

        String hash = passwordEncoder.encode(adminPassword);
        AdminUser admin = new AdminUser(adminEmail, hash);
        adminUserRepository.save(admin);
        log.info("Admin user '{}' created successfully.", adminEmail);
    }
}
