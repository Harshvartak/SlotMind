package com.slotmind.service;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {
  private final AppointmentRepository appointments;
  private final ProviderRepository providers;
  private final SlotRepository slots;

  @Transactional
  public Appointment book(
      String patientName, AppointmentType type, String providerId, String slotId) {
    Provider provider =
        providers.findById(providerId).orElseThrow(() -> Errors.notFound("Provider", providerId));
    Slot slot = slots.findById(slotId).orElseThrow(() -> Errors.notFound("Slot", slotId));
    requireBookable(provider, slot);
    slot.moveTo(SlotStatus.BOOKED);
    slots.saveAndFlush(slot); // @Version guards against a concurrent booking of the same slot
    return appointments.save(
        Appointment.builder()
            .id(Ids.next(Ids.APPOINTMENT_PREFIX))
            .patientName(patientName)
            .type(type)
            .provider(provider)
            .slot(slot)
            .status(AppointmentStatus.CONFIRMED)
            .build());
  }

  @Transactional(readOnly = true)
  public List<Appointment> list(AppointmentStatus status) {
    return status == null ? appointments.findAll() : appointments.findByStatus(status);
  }

  private static void requireBookable(Provider provider, Slot slot) {
    if (!slot.getProvider().getId().equals(provider.getId())) {
      throw Errors.badRequest("Slot " + slot.getId() + " does not belong to " + provider.getId());
    }
    if (provider.getStatus() == ProviderStatus.OFFLINE) {
      throw Errors.conflict("Provider is offline");
    }
    if (slot.getStatus() != SlotStatus.OPEN) {
      throw Errors.conflict("Slot is not open");
    }
  }
}
