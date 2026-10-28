package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.AppointmentResponse;
import com.healthcare.appointment.dto.BookingRequest;
import com.healthcare.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookingRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.book(authentication.getName(), request));
    }

    @GetMapping("/appointments/me")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public List<AppointmentResponse> mine(Authentication authentication) {
        return appointmentService.myAppointments(authentication.getName());
    }

    @PatchMapping("/appointments/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR','ADMIN')")
    public AppointmentResponse cancel(@PathVariable Long id, Authentication authentication) {
        return appointmentService.cancel(id, authentication.getName());
    }

    @PatchMapping("/appointments/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public AppointmentResponse complete(@PathVariable Long id, Authentication authentication) {
        return appointmentService.complete(id, authentication.getName());
    }

    @GetMapping("/admin/appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AppointmentResponse> all() {
        return appointmentService.allAppointments();
    }
}
