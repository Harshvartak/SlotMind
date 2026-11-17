package com.slotmind.repository;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
  List<Appointment> findByStatus(AppointmentStatus status);

  List<Appointment> findByProviderIdAndStatusIn(
      String providerId, Collection<AppointmentStatus> statuses);

  List<Appointment> findBySlotId(String slotId);
}
