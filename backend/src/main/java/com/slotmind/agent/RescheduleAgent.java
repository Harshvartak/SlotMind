package com.slotmind.agent;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.events.RescheduleRequested;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.service.SuggestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

/**
 * The agentic loop. Runs on another thread after the triggering request has committed and returned,
 * finds the affected confirmed bookings (see {@link AppointmentStatus#CONFIRMED_BOOKING}) and
 * queues a suggestion for each. The engine (rule or AI) is the same one the HTTP path uses, and
 * SuggestionService de-duplicates PENDING suggestions per appointment and trigger, so a repeated
 * event is harmless. Appointments already awaiting a decision are left alone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RescheduleAgent {
  private final AppointmentRepository appointments;
  private final SuggestionService suggestions;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRescheduleRequested(RescheduleRequested event) {
    List<Appointment> affected = affected(event);
    log.info(
        "{} {}: {} appointment(s) to reschedule",
        event.trigger(),
        event.subjectId(),
        affected.size());
    affected.forEach(a -> suggestSafely(a.getId(), event.trigger()));
  }

  private List<Appointment> affected(RescheduleRequested event) {
    return switch (event.trigger()) {
      case PROVIDER_OFFLINE ->
          appointments.findByProviderIdAndStatusIn(
              event.subjectId(), AppointmentStatus.CONFIRMED_BOOKING);
      case SLOT_CANCELLED ->
          appointments.findBySlotId(event.subjectId()).stream()
              .filter(a -> AppointmentStatus.CONFIRMED_BOOKING.contains(a.getStatus()))
              .toList();
      case INITIAL -> List.of(); // manual requests never go through the event
    };
  }

  /** One appointment failing must not stop the others from getting their suggestion. */
  private void suggestSafely(String appointmentId, TriggerReason trigger) {
    try {
      if (suggestions.suggest(appointmentId, trigger).isEmpty()) {
        log.warn("No alternative slot for {} ({})", appointmentId, trigger);
      }
    } catch (ResponseStatusException | DataAccessException e) {
      log.warn("Could not suggest for {} ({}): {}", appointmentId, trigger, e.getMessage());
    }
  }
}
