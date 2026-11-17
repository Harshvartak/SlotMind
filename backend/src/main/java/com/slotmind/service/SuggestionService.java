package com.slotmind.service;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingEngine;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.repository.SuggestionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {
  private final SuggestionRepository suggestions;
  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final SchedulingEngine engine;

  /**
   * Creates a PENDING suggestion for the appointment, or returns the existing one for the same
   * appointment and trigger (dedupe). Empty when no slot fits.
   */
  @Transactional
  public Optional<RescheduleSuggestion> suggest(String appointmentId, TriggerReason trigger) {
    Appointment appt =
        appointments
            .findById(appointmentId)
            .orElseThrow(() -> Errors.notFound("Appointment", appointmentId));
    Optional<RescheduleSuggestion> existing =
        suggestions.findFirstByAppointmentIdAndTriggerReasonAndStatus(
            appointmentId, trigger, SuggestionStatus.PENDING);
    if (existing.isPresent()) {
      return existing;
    }
    if (appt.getStatus() != AppointmentStatus.CONFIRMED
        && appt.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
      throw Errors.conflict("Appointment is " + appt.getStatus());
    }
    return engine.recommend(new SchedulingContext(appt, trigger)).stream()
        .findFirst()
        .map(best -> create(appt, trigger, best));
  }

  @Transactional(readOnly = true)
  public List<RescheduleSuggestion> list(SuggestionStatus status) {
    return status == null ? suggestions.findAll() : suggestions.findByStatus(status);
  }

  /** Front desk accepts or rejects, after confirming with the patient. */
  @Transactional
  public RescheduleSuggestion decide(String suggestionId, SuggestionStatus decision) {
    if (decision == SuggestionStatus.PENDING) {
      throw Errors.badRequest("status must be ACCEPTED or REJECTED");
    }
    RescheduleSuggestion s =
        suggestions
            .findById(suggestionId)
            .orElseThrow(() -> Errors.notFound("Suggestion", suggestionId));
    if (s.getStatus() != SuggestionStatus.PENDING) {
      throw Errors.conflict("Suggestion already " + s.getStatus());
    }
    if (decision == SuggestionStatus.ACCEPTED) {
      accept(s);
    } else {
      reject(s);
    }
    s.setStatus(decision);
    return suggestions.save(s);
  }

  private RescheduleSuggestion create(
      Appointment appt, TriggerReason trigger, Recommendation best) {
    appt.setStatus(AppointmentStatus.RESCHEDULE_PENDING);
    appointments.save(appt);
    RescheduleSuggestion s =
        RescheduleSuggestion.builder()
            .id(Ids.next(Ids.SUGGESTION_PREFIX))
            .appointment(appt)
            .recommendedSlotId(best.slotId())
            .confidence(best.confidence())
            .reasoning(best.reasoning())
            .status(SuggestionStatus.PENDING)
            .triggerReason(trigger)
            .createdAt(LocalDateTime.now())
            .build();
    best.alternateProviderId().ifPresent(s::setAlternateProviderId);
    return suggestions.save(s);
  }

  private void reject(RescheduleSuggestion s) {
    Appointment appt = s.getAppointment();
    appt.setStatus(AppointmentStatus.CONFIRMED);
    appointments.save(appt);
  }

  /**
   * Updates the appointment, the old slot and the new slot in one transaction. The new slot is
   * re-checked as OPEN, and its @Version rejects a concurrent accept (409).
   */
  private void accept(RescheduleSuggestion s) {
    Appointment appt = s.getAppointment();
    Slot target =
        slots
            .findById(s.getRecommendedSlotId())
            .orElseThrow(() -> Errors.conflict("Recommended slot no longer exists"));
    if (target.getStatus() != SlotStatus.OPEN) {
      throw Errors.conflict("Slot " + target.getId() + " is no longer open");
    }
    Slot old = appt.getSlot();
    if (old.getStatus() == SlotStatus.BOOKED) {
      old.setStatus(SlotStatus.OPEN); // a CANCELLED slot stays cancelled
    }
    target.setStatus(SlotStatus.BOOKED);
    slots.saveAndFlush(target); // throws on a concurrent version change
    slots.save(old);

    appt.setSlot(target);
    appt.setProvider(target.getProvider());
    appt.setStatus(AppointmentStatus.RESCHEDULED);
    appointments.save(appt);
  }
}
