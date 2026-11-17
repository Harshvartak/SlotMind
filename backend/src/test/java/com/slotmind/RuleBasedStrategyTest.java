package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.RuleBasedStrategy;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs against the seeded data; each test rolls back. APT-001 is PRV-001 (GENERAL, SLT-001). */
class RuleBasedStrategyTest extends ApiTestSupport {
  private static final int MAX_OPTIONS = 3;
  private static final int MINUTES_PER_SLOT = 30;

  @Autowired RuleBasedStrategy strategy;
  @Autowired SlotRepository slots;
  @Autowired ProviderRepository providers;
  @Autowired AppointmentRepository appointments;

  private void addOpenSlot(String id, String providerId, long minutesFromNow) {
    Provider provider = providers.findById(providerId).orElseThrow();
    slots.save(
        Slot.builder()
            .id(id)
            .provider(provider)
            .startTime(LocalDateTime.now().plusMinutes(minutesFromNow))
            .durationMinutes(MINUTES_PER_SLOT)
            .status(SlotStatus.OPEN)
            .build());
  }

  private List<Recommendation> recommendFor(String appointmentId) {
    Appointment appt = appointments.findById(appointmentId).orElseThrow();
    return strategy.recommend(new SchedulingContext(appt, TriggerReason.INITIAL));
  }

  @Test
  void sameProviderComesBeforeSameSpecialtyEvenWhenLater() {
    addOpenSlot("SLT-X1", "PRV-001", 300); // later than SLT-005 (PRV-004) but same provider

    List<Recommendation> r = recommendFor("APT-001");

    assertEquals("SLT-X1", r.get(0).slotId());
    assertTrue(r.get(0).alternateProviderId().isEmpty());
    assertEquals("SLT-005", r.get(1).slotId());
    assertEquals("PRV-004", r.get(1).alternateProviderId().orElseThrow());
  }

  @Test
  void alternateProviderReasoningNamesBothDoctorsInPlainLanguage() {
    Recommendation r = recommendFor("APT-001").get(0);

    assertTrue(r.reasoning().contains("Dr. Alice Chen"));
    assertTrue(r.reasoning().contains("Dr. David Kim"));
  }

  @Test
  void pastSlotsAreNeverRecommended() {
    addOpenSlot("SLT-PAST", "PRV-001", -60);

    List<Recommendation> r = recommendFor("APT-001");

    assertFalse(r.stream().anyMatch(x -> x.slotId().equals("SLT-PAST")));
  }

  @Test
  void slotsOfOfflineProvidersAreSkipped() {
    Provider other = providers.findById("PRV-004").orElseThrow();
    other.setStatus(ProviderStatus.OFFLINE);
    providers.save(other);

    assertTrue(recommendFor("APT-001").isEmpty());
  }

  @Test
  void theAppointmentsOwnSlotIsNeverRecommended() {
    assertFalse(recommendFor("APT-001").stream().anyMatch(x -> x.slotId().equals("SLT-001")));
  }

  @Test
  void returnsAtMostThreeOptionsNearestFirst() {
    for (int i = 1; i <= 4; i++) {
      addOpenSlot("SLT-Y" + i, "PRV-001", 400L + i);
    }

    List<Recommendation> r = recommendFor("APT-001");

    assertEquals(MAX_OPTIONS, r.size());
    assertEquals(
        List.of("SLT-Y1", "SLT-Y2", "SLT-Y3"), r.stream().map(Recommendation::slotId).toList());
  }
}
