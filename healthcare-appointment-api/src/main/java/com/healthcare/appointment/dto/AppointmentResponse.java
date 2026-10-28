package com.healthcare.appointment.dto;

import com.healthcare.appointment.model.Appointment;
import com.healthcare.appointment.model.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        String patientName,
        String doctorName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String reason,
        AppointmentStatus status) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getPatient().getName(),
                a.getDoctor().getName(),
                a.getStartTime(),
                a.getEndTime(),
                a.getReason(),
                a.getStatus());
    }
}
