
package com.karan.ev_charging_backend.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("reset-password")
public class PasswordResetRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String email = System.getenv("RESET_EMAIL");
        String newPassword = System.getenv("RESET_PASSWORD");

        if (email == null || newPassword == null
                || newPassword.length() < 8) {
            throw new IllegalStateException(
                "Set RESET_EMAIL and a RESET_PASSWORD of at least 8 characters."
            );
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() ->
                    new IllegalStateException("User not found.")
                );

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        System.out.println("Password reset completed.");
    }
}
