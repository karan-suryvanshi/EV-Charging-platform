package com.karan.ev_charging_backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthInterceptorTest {

    private JwtDecoder jwtDecoder;
    private WebSocketAuthInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        interceptor = new WebSocketAuthInterceptor(jwtDecoder);
        channel = mock(MessageChannel.class);
    }

    private Message<byte[]> createMessage(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt createMockJwt(Long userId) {
        return new Jwt(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "user@example.com", "userId", userId)
        );
    }

    // 1. CONNECT with valid bearer token.
    @Test
    void connect_withValidBearerToken_authenticatesUser() {
        Jwt jwt = createMockJwt(100L);
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");

        Message<?> result = interceptor.preSend(createMessage(accessor), channel);

        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("100", resultAccessor.getUser().getName());
    }

    // 2. CONNECT without Authorization header.
    @Test
    void connect_withoutAuthorizationHeader_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("Missing or invalid Authorization header", ex.getMessage());
    }

    // 3. CONNECT with non-Bearer authorization scheme.
    @Test
    void connect_withNonBearerScheme_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Basic dXNlcjpwYXNz");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("Missing or invalid Authorization header", ex.getMessage());
    }

    // 4. CONNECT with missing userId claim.
    @Test
    void connect_withMissingUserIdClaim_throwsIllegalArgumentException() {
        Jwt jwtWithoutUserId = new Jwt(
                "token-without-user-id",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "user@example.com")
        );
        when(jwtDecoder.decode("token-without-user-id")).thenReturn(jwtWithoutUserId);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token-without-user-id");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("JWT userId claim is missing", ex.getMessage());
    }

    // 5. CONNECT with malformed JWT.
    @Test
    void connect_withMalformedJwt_propagatesJwtException() {
        when(jwtDecoder.decode("malformed-token")).thenThrow(new JwtException("Invalid JWT signature"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer malformed-token");

        JwtException ex = assertThrows(
                JwtException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("Invalid JWT signature", ex.getMessage());
    }

    // 6. SUBSCRIBE to own notification topic.
    @Test
    void subscribe_toOwnNotificationTopic_allowsSubscription() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/notifications/100");
        accessor.setUser(() -> "100");

        Message<?> result = interceptor.preSend(createMessage(accessor), channel);

        assertNotNull(result);
    }

    // 7. SUBSCRIBE to another user's notification topic.
    @Test
    void subscribe_toAnotherUserNotificationTopic_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/notifications/200");
        accessor.setUser(() -> "100");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("You are not allowed to subscribe to this topic", ex.getMessage());
    }

    // 8. SUBSCRIBE to arbitrary topic.
    @Test
    void subscribe_toArbitraryTopic_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/public-announcements");
        accessor.setUser(() -> "100");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("You are not allowed to subscribe to this topic", ex.getMessage());
    }

    // 9. SUBSCRIBE without authenticated Principal.
    @Test
    void subscribe_withoutAuthenticatedPrincipal_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/notifications/100");
        // No principal set

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("Unauthenticated subscription is not allowed", ex.getMessage());
    }

    // 10. SEND to another topic is rejected.
    @Test
    void send_toAnotherTopic_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/notifications/200");
        accessor.setUser(() -> "100");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("Client message publishing is not permitted", ex.getMessage());
    }

    // 11. SEND to own topic is rejected.
    @Test
    void send_toOwnTopic_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/notifications/100");
        accessor.setUser(() -> "100");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preSend(createMessage(accessor), channel)
        );

        assertEquals("Client message publishing is not permitted", ex.getMessage());
    }

    // 12. DISCONNECT and UNSUBSCRIBE pass through.
    @Test
    void disconnectAndUnsubscribe_passThroughWithoutException() {
        StompHeaderAccessor disconnectAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<?> disconnectResult = interceptor.preSend(createMessage(disconnectAccessor), channel);
        assertNotNull(disconnectResult);

        StompHeaderAccessor unsubscribeAccessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        Message<?> unsubscribeResult = interceptor.preSend(createMessage(unsubscribeAccessor), channel);
        assertNotNull(unsubscribeResult);

        // Accessor for heartbeat
        StompHeaderAccessor heartbeatAccessor = StompHeaderAccessor.createForHeartbeat();
        Message<?> heartbeatResult = interceptor.preSend(createMessage(heartbeatAccessor), channel);
        assertNotNull(heartbeatResult);
    }
}
