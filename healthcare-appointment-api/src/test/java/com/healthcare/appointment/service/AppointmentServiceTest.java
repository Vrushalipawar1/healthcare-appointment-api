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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AppointmentService service;

    private User patient;
    private User doctor;
    private User otherPatient;
    private User admin;
    private LocalDateTime start;

    @BeforeEach
    void setUp() {
        patient = user(1L, "Pat", "pat@example.com", Role.PATIENT);
        doctor = user(2L, "Dr Who", "doc@example.com", Role.DOCTOR);
        otherPatient = user(3L, "Other", "other@example.com", Role.PATIENT);
        admin = user(4L, "Admin", "admin@example.com", Role.ADMIN);
        start = LocalDateTime.now().plusDays(1).withNano(0);
    }

    // ---------- book ----------

    @Test
    void book_success_setsEndTimeFromDuration_andStatusBooked() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.countOverlapping(eq(2L), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = service.book("pat@example.com",
                new BookingRequest(2L, start, 30, "Check-up"));

        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.endTime()).isEqualTo(start.plusMinutes(30));
        assertThat(response.patientName()).isEqualTo("Pat");
        assertThat(response.doctorName()).isEqualTo("Dr Who");
    }

    @Test
    void book_checksOverlapUsingTheExactSlotWindow() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.countOverlapping(eq(2L), any(), any())).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.book("pat@example.com", new BookingRequest(2L, start, 45, null));

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(appointmentRepository).countOverlapping(eq(2L), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(start);
        assertThat(to.getValue()).isEqualTo(start.plusMinutes(45));
    }

    @Test
    void book_unknownDoctor_throwsNotFound() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book("pat@example.com", new BookingRequest(99L, start, 30, null)))
                .isInstanceOf(NotFoundException.class);

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void book_targetUserIsNotADoctor_throwsNotFound() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherPatient));

        assertThatThrownBy(() -> service.book("pat@example.com", new BookingRequest(3L, start, 30, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Doctor not found");
    }

    @Test
    void book_overlappingSlot_throwsConflict() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(userRepository.findById(2L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.countOverlapping(eq(2L), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.book("pat@example.com", new BookingRequest(2L, start, 30, null)))
                .isInstanceOf(ConflictException.class);

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    // ---------- list ----------

    @Test
    void myAppointments_forPatient_usesPatientQuery() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(appointmentRepository.findByPatientIdOrderByStartTime(1L))
                .thenReturn(List.of(appointment(10L, AppointmentStatus.BOOKED)));

        List<AppointmentResponse> result = service.myAppointments("pat@example.com");

        assertThat(result).hasSize(1);
        verify(appointmentRepository, never()).findByDoctorIdOrderByStartTime(any());
    }

    @Test
    void myAppointments_forDoctor_usesDoctorQuery() {
        when(userRepository.findByEmail("doc@example.com")).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorIdOrderByStartTime(2L))
                .thenReturn(List.of(appointment(10L, AppointmentStatus.BOOKED)));

        List<AppointmentResponse> result = service.myAppointments("doc@example.com");

        assertThat(result).hasSize(1);
        verify(appointmentRepository, never()).findByPatientIdOrderByStartTime(any());
    }

    // ---------- cancel ----------

    @Test
    void cancel_byOwningPatient_setsCancelled() {
        stubAppointment(10L, AppointmentStatus.BOOKED);
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = service.cancel(10L, "pat@example.com");

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_byTreatingDoctor_setsCancelled() {
        stubAppointment(10L, AppointmentStatus.BOOKED);
        when(userRepository.findByEmail("doc@example.com")).thenReturn(Optional.of(doctor));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancel(10L, "doc@example.com").status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_byAdmin_isAllowed() {
        stubAppointment(10L, AppointmentStatus.BOOKED);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancel(10L, "admin@example.com").status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_byUnrelatedPatient_isForbidden_andNothingIsSaved() {
        stubAppointment(10L, AppointmentStatus.BOOKED);
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherPatient));

        assertThatThrownBy(() -> service.cancel(10L, "other@example.com"))
                .isInstanceOf(ForbiddenException.class);

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        stubAppointment(10L, AppointmentStatus.CANCELLED);
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.cancel(10L, "pat@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_unknownAppointment_throwsNotFound() {
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L, "pat@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- complete ----------

    @Test
    void complete_byTreatingDoctor_setsCompleted() {
        stubAppointment(10L, AppointmentStatus.BOOKED);
        when(userRepository.findByEmail("doc@example.com")).thenReturn(Optional.of(doctor));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.complete(10L, "doc@example.com").status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void complete_byPatient_isForbidden() {
        stubAppointment(10L, AppointmentStatus.BOOKED);
        when(userRepository.findByEmail("pat@example.com")).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.complete(10L, "pat@example.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void complete_alreadyCompleted_throwsConflict() {
        stubAppointment(10L, AppointmentStatus.COMPLETED);
        when(userRepository.findByEmail("doc@example.com")).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.complete(10L, "doc@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- helpers ----------

    private void stubAppointment(Long id, AppointmentStatus status) {
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(appointment(id, status)));
    }

    private Appointment appointment(Long id, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setStartTime(start);
        a.setEndTime(start.plusMinutes(30));
        a.setStatus(status);
        return a;
    }

    private static User user(Long id, String name, String email, Role role) {
        User u = new User(name, email, "hash", role);
        u.setId(id);
        return u;
    }
}
