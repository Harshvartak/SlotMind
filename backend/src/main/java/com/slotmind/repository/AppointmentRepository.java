package com.slotmind.repository;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
  List<Appointment> findByStatus(AppointmentStatus status);

  List<Appointment> findByProviderIdAndStatus(String providerId, AppointmentStatus status);

  List<Appointment> findBySlotId(String slotId);
}
