package com.healthcare.appointment.repository;

import com.healthcare.appointment.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientIdOrderByStartTime(Long patientId);

    List<Appointment> findByDoctorIdOrderByStartTime(Long doctorId);

    /**
     * Counts the doctor's BOOKED appointments that overlap [start, end).
     * Two slots overlap when each one starts before the other ends, so back-to-back
     * slots (one ends exactly when the next starts) do not clash.
     */
    @Query("""
            select count(a) from Appointment a
            where a.doctor.id = :doctorId
              and a.status = com.healthcare.appointment.model.AppointmentStatus.BOOKED
              and a.startTime < :end
              and a.endTime > :start
            """)
    long countOverlapping(@Param("doctorId") Long doctorId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);
}
