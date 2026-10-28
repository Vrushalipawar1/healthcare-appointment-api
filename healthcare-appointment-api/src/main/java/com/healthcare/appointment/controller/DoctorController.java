package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.DoctorSummary;
import com.healthcare.appointment.model.Role;
import com.healthcare.appointment.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final UserRepository userRepository;

    public DoctorController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<DoctorSummary> listDoctors() {
        return userRepository.findByRole(Role.DOCTOR).stream()
                .map(d -> new DoctorSummary(d.getId(), d.getName()))
                .toList();
    }
}
