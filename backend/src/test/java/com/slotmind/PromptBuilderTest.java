package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.slotmind.ai.PromptBuilder;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.TriggerReason;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {
  private static final String PATIENT = "Maria Garcia";
  private static final int DURATION = 30;

  private final Provider current =
      Provider.builder()
          .id("PRV-001")
          .name("Dr. Alice Chen")
          .specialty(Specialty.GENERAL)
          .status(ProviderStatus.AVAILABLE)
          .build();
  private final Provider other =
      Provider.builder()
          .id("PRV-004")
          .name("Dr. David Kim")
          .specialty(Specialty.GENERAL)
          .status(ProviderStatus.AVAILABLE)
          .build();
  private final Slot own = slot("SLT-001", current);
  private final Slot candidate = slot("SLT-005", other);
  private final Appointment appt =
      Appointment.builder()
          .id("APT-001")
          .patientName(PATIENT)
          .type(AppointmentType.URGENT)
          .provider(current)
          .slot(own)
          .status(AppointmentStatus.CONFIRMED)
          .build();

  private Slot slot(String id, Provider provider) {
    return Slot.builder()
        .id(id)
        .provider(provider)
        .startTime(LocalDateTime.now().plusHours(1))
        .durationMinutes(DURATION)
        .status(SlotStatus.OPEN)
        .build();
  }

  private String prompt(TriggerReason trigger) {
    return PromptBuilder.build(appt, trigger, List.of(candidate), List.of(current, other));
  }

  @Test
  void providerOfflinePromptAsksForSameSpecialtyAndFavoursUrgency() {
    String p = prompt(TriggerReason.PROVIDER_OFFLINE);

    assertTrue(p.contains("is OFFLINE"));
    assertTrue(p.contains("same specialty"));
    assertTrue(p.contains("URGENT"));
    assertFalse(p.contains("was CANCELLED"));
  }

  @Test
  void slotCancelledPromptPrefersTheSameProvider() {
    String p = prompt(TriggerReason.SLOT_CANCELLED);

    assertTrue(p.contains("was CANCELLED"));
    assertTrue(p.contains("SAME provider"));
    assertFalse(p.contains("is OFFLINE"));
  }

  @Test
  void manualRequestOnAnOfflineProviderUsesTheOfflinePrompt() {
    current.setStatus(ProviderStatus.OFFLINE);

    assertTrue(prompt(TriggerReason.INITIAL).contains("is OFFLINE"));
  }

  @Test
  void promptListsCandidateSlotsAndTheRoster() {
    String p = prompt(TriggerReason.SLOT_CANCELLED);

    assertTrue(p.contains("slotId=SLT-005 providerId=PRV-004"));
    assertTrue(p.contains("providerId=PRV-004 name=Dr. David Kim specialty=GENERAL"));
  }

  @Test
  void promptDemandsPlainSpokenReasoningAndOmitsThePatientName() {
    String p = prompt(TriggerReason.SLOT_CANCELLED);

    assertTrue(p.contains("read aloud to the patient"));
    assertTrue(p.contains("no jargon"));
    assertFalse(p.contains(PATIENT));
  }
}
