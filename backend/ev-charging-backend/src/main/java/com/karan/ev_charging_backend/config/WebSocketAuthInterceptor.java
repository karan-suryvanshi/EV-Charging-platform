package com.karan.ev_charging_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Step 1: Authenticate the WebSocket connection
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorizationHeaders =
                    accessor.getNativeHeader("Authorization");

            if (authorizationHeaders == null
                    || authorizationHeaders.isEmpty()
                    || !authorizationHeaders.get(0)
                    .startsWith("Bearer ")) {
                throw new IllegalArgumentException(
                        "Missing or invalid Authorization header");
            }

            String token = authorizationHeaders.get(0)
                    .substring(7);

            Jwt jwt = jwtDecoder.decode(token);

            // The JWT subject is the user's email, but the notification
            // topics and frontend use the numeric user ID. Extract the
            // "userId" claim so the Principal name matches the topic.
            Object userIdClaim = jwt.getClaim("userId");

            if (userIdClaim == null) {
                throw new IllegalArgumentException(
                        "JWT userId claim is missing");
            }

            String authenticatedUserId = String.valueOf(userIdClaim);

            log.debug("WebSocket CONNECT authenticated for user ID: {}",
                    authenticatedUserId);

            accessor.setUser(() ->
                    authenticatedUserId);
        }

        // Step 2: Allow users to subscribe only to their own topic
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

            Principal principal = accessor.getUser();
            String destination = accessor.getDestination();

            if (principal == null || destination == null) {
                throw new IllegalArgumentException(
                        "Unauthenticated subscription is not allowed");
            }

            String allowedDestination =
                    "/topic/notifications/" + principal.getName();

            if (!allowedDestination.equals(destination)) {
                log.warn("User {} attempted to subscribe to unauthorized "
                                + "destination: {}",
                        principal.getName(), destination);
                throw new IllegalArgumentException(
                        "You are not allowed to subscribe to this topic");
            }

            log.debug("User {} subscribed to {}", principal.getName(),
                    destination);
        }

        // Step 3: Reject all client SEND messages (the platform is strictly server-to-client)
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new IllegalArgumentException(
                    "Client message publishing is not permitted");
        }

        return message;
    }
}
