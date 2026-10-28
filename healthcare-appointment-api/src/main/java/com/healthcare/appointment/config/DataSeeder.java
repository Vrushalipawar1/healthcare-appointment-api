package com.healthcare.appointment.config;

import com.healthcare.appointment.model.Role;
import com.healthcare.appointment.model.User;
import com.healthcare.appointment.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo data for local runs only (app.seed.enabled=true). ADMIN accounts can't be
 * created through the API, so this is how you get one while developing.
 * Turn it off (app.seed.enabled=false) and rotate these passwords for any real deployment.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("Admin", "admin@healthcare.local", "Admin@1234", Role.ADMIN);
        seed("Dr. Demo Doctor", "doctor@healthcare.local", "Doctor@1234", Role.DOCTOR);
    }

    private void seed(String name, String email, String rawPassword, Role role) {
        if (!userRepository.existsByEmail(email)) {
            userRepository.save(new User(name, email, passwordEncoder.encode(rawPassword), role));
        }
    }
}
