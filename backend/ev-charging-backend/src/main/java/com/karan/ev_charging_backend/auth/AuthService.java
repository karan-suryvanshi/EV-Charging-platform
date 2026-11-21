
package com.karan.ev_charging_backend.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String name, String email, String password) {

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists."
            );
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);

        return userRepository.save(user);

    }
 
public User login(String email, String password) {
    User user = userRepository.findByEmailIgnoreCase(email.trim())
        .orElseThrow(() ->
            new IllegalArgumentException("Invalid email or password.")
        );

    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new IllegalArgumentException("Invalid email or password.");
    }

    return user;
}

}
