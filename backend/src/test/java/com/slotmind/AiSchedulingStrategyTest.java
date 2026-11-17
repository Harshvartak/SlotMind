package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.slotmind.ai.AiException;
import com.slotmind.ai.AiSchedulingStrategy;
import com.slotmind.ai.LlmGateway;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * The model is stubbed; the seed has PRV-001 (GENERAL) offline, so the rule fallback for APT-001 is
 * SLT-005 on PRV-004 with confidence 0.75.
 */
class AiSchedulingStrategyTest extends ApiTestSupport {
  private static final String REPLY =
      "{\"slotId\":\"%s\",\"alternateProviderId\":\"%s\",\"confidence\":%s,\"reasoning\":\"%s\"}";
  private static final String PLAIN = "Dr. Kim has an opening that suits you.";
  private static final double RULE_CONFIDENCE = 0.75;

  @MockBean LlmGateway gateway;
  @Autowired AiSchedulingStrategy ai;
  @Autowired AppointmentRepository appointments;
  @Autowired ProviderRepository providers;

  private List<Recommendation> recommendFor(String appointmentId, String modelReply) {
    Provider p1 = providers.findById("PRV-001").orElseThrow();
    p1.setStatus(ProviderStatus.OFFLINE);
    providers.save(p1);
    when(gateway.callLLM(anyString())).thenReturn(modelReply);
    Appointment appt = appointments.findById(appointmentId).orElseThrow();
    return ai.recommend(new SchedulingContext(appt, TriggerReason.PROVIDER_OFFLINE));
  }

  private void assertFellBackToRules(List<Recommendation> r) {
    assertEquals("SLT-005", r.get(0).slotId());
    assertEquals(RULE_CONFIDENCE, r.get(0).confidence());
  }

  @Test
  void validReplyIsUsedWithTheModelsConfidenceAndReasoning() {
    List<Recommendation> r =
        recommendFor("APT-001", REPLY.formatted("SLT-005", "PRV-004", "0.91", PLAIN));

    assertEquals(1, r.size());
    assertEquals("SLT-005", r.get(0).slotId());
    assertEquals(Optional.of("PRV-004"), r.get(0).alternateProviderId());
    assertEquals(0.91, r.get(0).confidence());
    assertEquals(PLAIN, r.get(0).reasoning());
  }

  @Test
  void sameProviderReplyHasNoAlternateProvider() {
    List<Recommendation> r =
        recommendFor(
            "APT-003", REPLY.formatted("SLT-004", "", "0.8", PLAIN)); // PRV-002 keeps APT-003

    assertEquals("SLT-004", r.get(0).slotId());
    assertTrue(r.get(0).alternateProviderId().isEmpty());
  }

  @Test
  void markdownFencedJsonIsAccepted() {
    String fenced = "```json\n" + REPLY.formatted("SLT-005", "PRV-004", "0.9", PLAIN) + "\n```";

    assertEquals(0.9, recommendFor("APT-001", fenced).get(0).confidence());
  }

  @Test
  void badJsonFallsBackToRules() {
    assertFellBackToRules(recommendFor("APT-001", "Sure! I would pick the first one."));
  }

  @Test
  void unknownSlotIdFallsBackToRules() {
    assertFellBackToRules(
        recommendFor("APT-001", REPLY.formatted("SLT-999", "PRV-004", "0.9", PLAIN)));
  }

  @Test
  void nonOpenSlotFallsBackToRules() {
    assertFellBackToRules( // SLT-001 is BOOKED
        recommendFor("APT-001", REPLY.formatted("SLT-001", "PRV-001", "0.9", PLAIN)));
  }

  @Test
  void providerThatDoesNotOwnTheSlotFallsBackToRules() {
    assertFellBackToRules( // SLT-005 belongs to PRV-004, not PRV-002
        recommendFor("APT-001", REPLY.formatted("SLT-005", "PRV-002", "0.9", PLAIN)));
  }

  @Test
  void unknownProviderIdFallsBackToRules() {
    assertFellBackToRules(
        recommendFor("APT-001", REPLY.formatted("SLT-005", "PRV-999", "0.9", PLAIN)));
  }

  @Test
  void confidenceOutsideZeroToOneFallsBackToRules() {
    assertFellBackToRules(
        recommendFor("APT-001", REPLY.formatted("SLT-005", "PRV-004", "1.5", PLAIN)));
  }

  @Test
  void emptyReasoningFallsBackToRules() {
    assertFellBackToRules(
        recommendFor("APT-001", REPLY.formatted("SLT-005", "PRV-004", "0.9", "")));
  }

  @Test
  void gatewayFailureFallsBackToRules() {
    when(gateway.callLLM(anyString())).thenThrow(new AiException("model is down"));
    Provider p1 = providers.findById("PRV-001").orElseThrow();
    p1.setStatus(ProviderStatus.OFFLINE);
    providers.save(p1);
    Appointment appt = appointments.findById("APT-001").orElseThrow();

    assertFellBackToRules(
        ai.recommend(new SchedulingContext(appt, TriggerReason.PROVIDER_OFFLINE)));
  }
}
