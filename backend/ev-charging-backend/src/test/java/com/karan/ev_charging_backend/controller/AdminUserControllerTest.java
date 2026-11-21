package com.karan.ev_charging_backend.controller;

import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserControllerTest {

    @Test
    void returnsNonAdminUsersWithoutSensitiveFields() {
        User user = new User();
        user.setId(7L);
        user.setName("Registered User");
        user.setEmail("user@example.test");
        user.setPassword("must-not-be-returned");
        user.setRole(UserRole.USER);

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByRoleNotOrderByIdAsc(UserRole.ADMIN))
                .thenReturn(List.of(user));

        ResponseEntity<List<Map<String, Object>>> response =
                new AdminUserController(userRepository).getAllUsers();

        assertEquals(1, response.getBody().size());
        assertEquals(
                Set.of("id", "name", "email", "role"),
                response.getBody().get(0).keySet());
        verify(userRepository).findByRoleNotOrderByIdAsc(UserRole.ADMIN);
    }
}
