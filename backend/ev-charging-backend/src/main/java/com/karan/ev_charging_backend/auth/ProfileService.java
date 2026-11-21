
package com.karan.ev_charging_backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        return ProfileResponse.from(user);
    }

    @Transactional
    public ProfileResponse updateProfile(
            Long userId,
            ProfileUpdateRequest request) {

        User user = findUser(userId);

        user.setName(request.name().trim());

        User updatedUser = userRepository.save(user);

        return ProfileResponse.from(updatedUser);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."
                ));
    }
}
