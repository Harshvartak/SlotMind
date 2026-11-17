package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.RuleBasedStrategy;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingContextFactory;
import com.slotmind.repository.AppointmentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Runs against the brief's seed; each test rolls back. APT-001 is PRV-001 (general, SLT-001); the
 * open slots are SLT-004 (PRV-002, specialist) and SLT-005 (PRV-004, general).
 */
class RuleBasedStrategyTest extends ApiTestSupport {
  private static final int MAX_OPTIONS = 3;

  @Autowired RuleBasedStrategy strategy;
  @Autowired SchedulingContextFactory contexts;
  @Autowired AppointmentRepository appointments;

  private SchedulingContext contextFor(String appointmentId) {
    Appointment appt = appointments.findById(appointmentId).orElseThrow();
    return contexts.create(appt, TriggerReason.INITIAL);
  }

  private List<Recommendation> recommendFor(String appointmentId) {
    return strategy.recommend(contextFor(appointmentId));
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

    assertTrue(r.reasoning().contains("Dr. Sarah Chen"));
    assertTrue(r.reasoning().contains("Dr. Rahul Verma"));
  }

  @Test
  void onlyOffersSlotsFromTheContextItIsGiven() {
    SchedulingContext full = contextFor("APT-001");
    SchedulingContext none =
        new SchedulingContext(full.appointment(), full.trigger(), List.of(), full.providers());

    assertTrue(strategy.recommend(none).isEmpty());
    assertFalse(strategy.recommend(full).isEmpty());
  }

  @Test
  void pastSlotsAreNeverInTheContextSoNeverRecommended() {
    addOpenSlot("SLT-PAST", "PRV-001", -60);

    List<Recommendation> r = recommendFor("APT-001");

    assertFalse(r.stream().anyMatch(x -> x.slotId().equals("SLT-PAST")));
  }

  @Test
  void slotsOfOfflineProvidersAreNeverInTheContextSoNeverRecommended() {
    Provider other = providerRepository.findById("PRV-004").orElseThrow();
    other.setStatus(ProviderStatus.OFFLINE);
    providerRepository.save(other);

    assertTrue(recommendFor("APT-001").isEmpty()); // SLT-004 is a specialist's, not a match
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
