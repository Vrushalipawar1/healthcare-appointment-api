package com.healthcare.appointment.security;

import com.healthcare.appointment.model.Role;
import com.healthcare.appointment.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-chars";
    private final User user = new User("Asha", "asha@example.com", "hash", Role.PATIENT);

    @Test
    void generatedToken_isValid_andCarriesTheEmail() {
        JwtService jwtService = new JwtService(SECRET, 60);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("asha@example.com");
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        String token = new JwtService("another-secret-key-that-is-also-32-chars-long", 60).generateToken(user);

        assertThat(new JwtService(SECRET, 60).isTokenValid(token)).isFalse();
    }

    @Test
    void expiredToken_isRejected() {
        JwtService expiredIssuer = new JwtService(SECRET, -1);

        String token = expiredIssuer.generateToken(user);

        assertThat(expiredIssuer.isTokenValid(token)).isFalse();
    }

    @Test
    void garbageToken_isRejected() {
        JwtService jwtService = new JwtService(SECRET, 60);

        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}
