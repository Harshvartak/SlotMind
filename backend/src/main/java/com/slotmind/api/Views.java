package com.slotmind.api;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import java.time.LocalDateTime;
import java.util.Optional;

/** API response shapes. Controllers return these, never entities. */
public final class Views {
  private Views() {}

  public record ProviderView(String id, String name, Specialty specialty, ProviderStatus status) {
    static ProviderView of(Provider p) {
      return new ProviderView(p.getId(), p.getName(), p.getSpecialty(), p.getStatus());
    }
  }

  public record SlotView(
      String id,
      String providerId,
      LocalDateTime startTime,
      int durationMinutes,
      SlotStatus status) {
    static SlotView of(Slot s) {
      return new SlotView(
          s.getId(),
          s.getProvider().getId(),
          s.getStartTime(),
          s.getDurationMinutes(),
          s.getStatus());
    }
  }

  public record AppointmentView(
      String id,
      String patientName,
      AppointmentType type,
      ProviderView provider,
      SlotView slot,
      AppointmentStatus status) {
    static AppointmentView of(Appointment a) {
      return new AppointmentView(
          a.getId(),
          a.getPatientName(),
          a.getType(),
          ProviderView.of(a.getProvider()),
          SlotView.of(a.getSlot()),
          a.getStatus());
    }
  }

  public record SuggestionView(
      String id,
      AppointmentView appointment,
      String recommendedSlotId,
      Optional<String> alternateProviderId,
      double confidence,
      String reasoning,
      SuggestionStatus status,
      TriggerReason triggerReason,
      LocalDateTime createdAt) {
    static SuggestionView of(RescheduleSuggestion s) {
      return new SuggestionView(
          s.getId(),
          AppointmentView.of(s.getAppointment()),
          s.getRecommendedSlotId(),
          s.alternateProvider(),
          s.getConfidence(),
          s.getReasoning(),
          s.getStatus(),
          s.getTriggerReason(),
          s.getCreatedAt());
    }
  }
}
