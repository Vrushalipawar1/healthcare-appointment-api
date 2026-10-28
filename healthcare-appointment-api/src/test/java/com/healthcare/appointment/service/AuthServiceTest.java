package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AuthResponse;
import com.healthcare.appointment.dto.LoginRequest;
import com.healthcare.appointment.dto.RegisterRequest;
import com.healthcare.appointment.exception.ApiExceptions.BadRequestException;
import com.healthcare.appointment.exception.ApiExceptions.ConflictException;
import com.healthcare.appointment.exception.ApiExceptions.UnauthorizedException;
import com.healthcare.appointment.model.Role;
import com.healthcare.appointment.model.User;
import com.healthcare.appointment.repository.UserRepository;
import com.healthcare.appointment.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private AuthService authService;

    @Test
    void register_savesHashedPassword_normalisesEmail_andReturnsToken() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("token-123");

        AuthResponse response = authService.register(
                new RegisterRequest(" Asha ", "  Asha@Example.com ", "Password1", Role.PATIENT));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("HASH");
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Asha");
        assertThat(response.token()).isEqualTo("token-123");
        assertThat(response.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void register_adminRole_isRejected_withoutTouchingTheDatabase() {
        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Eve", "eve@example.com", "Password1", Role.ADMIN)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Asha", "asha@example.com", "Password1", Role.PATIENT)))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_withCorrectPassword_returnsToken() {
        User user = new User("Asha", "asha@example.com", "HASH", Role.DOCTOR);
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "HASH")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("token-abc");

        AuthResponse response = authService.login(new LoginRequest("asha@example.com", "Password1"));

        assertThat(response.token()).isEqualTo("token-abc");
        assertThat(response.role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void login_withWrongPassword_throwsUnauthorized() {
        User user = new User("Asha", "asha@example.com", "HASH", Role.PATIENT);
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_withUnknownEmail_throwsSameUnauthorized_andNeverChecksPassword() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Password1")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(passwordEncoder);
    }
}
