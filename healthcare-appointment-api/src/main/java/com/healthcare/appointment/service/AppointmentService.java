package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AppointmentResponse;
import com.healthcare.appointment.dto.BookingRequest;
import com.healthcare.appointment.exception.ApiExceptions.ConflictException;
import com.healthcare.appointment.exception.ApiExceptions.ForbiddenException;
import com.healthcare.appointment.exception.ApiExceptions.NotFoundException;
import com.healthcare.appointment.model.Appointment;
import com.healthcare.appointment.model.AppointmentStatus;
import com.healthcare.appointment.model.Role;
import com.healthcare.appointment.model.User;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AppointmentResponse book(String patientEmail, BookingRequest request) {
        User patient = findUser(patientEmail);
        User doctor = userRepository.findById(request.doctorId())
                .filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        LocalDateTime start = request.startTime();
        LocalDateTime end = start.plusMinutes(request.durationMinutes());

        if (appointmentRepository.countOverlapping(doctor.getId(), start, end) > 0) {
            throw new ConflictException("Doctor is already booked for that time slot");
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        appointment.setReason(request.reason());
        appointment.setStatus(AppointmentStatus.BOOKED);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myAppointments(String email) {
        User user = findUser(email);
        List<Appointment> appointments = (user.getRole() == Role.DOCTOR)
                ? appointmentRepository.findByDoctorIdOrderByStartTime(user.getId())
                : appointmentRepository.findByPatientIdOrderByStartTime(user.getId());
        return appointments.stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> allAppointments() {
        return appointmentRepository.findAll().stream().map(AppointmentResponse::from).toList();
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String email) {
        User user = findUser(email);
        Appointment appointment = findAppointment(appointmentId);

        boolean isParticipant = appointment.getPatient().getId().equals(user.getId())
                || appointment.getDoctor().getId().equals(user.getId());
        if (!isParticipant && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("You can only cancel your own appointments");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException("Only BOOKED appointments can be cancelled");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse complete(Long appointmentId, String email) {
        User user = findUser(email);
        Appointment appointment = findAppointment(appointmentId);

        if (!appointment.getDoctor().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the treating doctor can complete an appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException("Only BOOKED appointments can be completed");
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
    }
}
