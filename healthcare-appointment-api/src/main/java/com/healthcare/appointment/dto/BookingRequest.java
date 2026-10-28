package com.healthcare.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BookingRequest(
        @NotNull(message = "doctorId is required") Long doctorId,
        @NotNull(message = "startTime is required") @Future(message = "startTime must be in the future") LocalDateTime startTime,
        @Min(value = 15, message = "durationMinutes must be at least 15")
        @Max(value = 120, message = "durationMinutes must be at most 120") int durationMinutes,
        String reason) {
}
