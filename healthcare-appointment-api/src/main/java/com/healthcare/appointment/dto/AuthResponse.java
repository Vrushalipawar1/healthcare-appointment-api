package com.healthcare.appointment.dto;

import com.healthcare.appointment.model.Role;

public record AuthResponse(String token, String name, Role role) {
}
