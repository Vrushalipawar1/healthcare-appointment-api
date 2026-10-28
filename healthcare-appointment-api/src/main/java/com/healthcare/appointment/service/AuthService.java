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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        // Anyone can call this endpoint, so privileged roles must never be self-assignable.
        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("Role ADMIN cannot be self-registered");
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = userRepository.save(
                new User(request.name().trim(), email, passwordEncoder.encode(request.password()), request.role()));
        return new AuthResponse(jwtService.generateToken(user), user.getName(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        // Same message for "no such user" and "wrong password" so accounts can't be enumerated.
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return new AuthResponse(jwtService.generateToken(user), user.getName(), user.getRole());
    }
}
