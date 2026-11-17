package com.slotmind.engine;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The one place that reads slots and providers for scheduling, so strategies never touch data. */
@Component
@RequiredArgsConstructor
public class SchedulingContextFactory {
  private final SlotRepository slots;
  private final ProviderRepository providers;

  public SchedulingContext create(Appointment appointment, TriggerReason trigger) {
    return new SchedulingContext(
        appointment,
        trigger,
        slots
            .findByStatusAndStartTimeAfterOrderByStartTimeAsc(SlotStatus.OPEN, LocalDateTime.now())
            .stream()
            .filter(s -> !s.getId().equals(appointment.getSlot().getId()))
            .filter(s -> s.getProvider().getStatus() != ProviderStatus.OFFLINE)
            .toList(),
        providers.findAll());
  }
}
