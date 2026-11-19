package com.example.lets_play.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.lets_play.users.UserRepository;
import com.example.lets_play.users.models.Role;
import com.example.lets_play.users.models.User;

@Component 
public class AdminSeeder implements CommandLineRunner{
    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder, @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override 
    public void run(String... args) {
        if (email.isBlank() || password.length() < 8) {
            log.warn("ADMIN_EMAIL/PASSWORD not set");
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) return;
        users.save(new User("Admin", normalized, encoder.encode(password), Role.ADMIN));
        log.info("Seeded admin account {}", normalized);
    }
}
