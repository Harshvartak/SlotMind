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
import com.slotmind.engine.SchedulingContextFactory;
import com.slotmind.repository.AppointmentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * The model is stubbed. PRV-001 (general) is taken offline, so the rule fallback for APT-001 is
 * SLT-005 on PRV-004 with confidence 0.75. The model may only choose among the context's open
 * slots.
 */
class AiSchedulingStrategyTest extends ApiTestSupport {
  private static final String REPLY =
      "{\"slotId\":\"%s\",\"alternateProviderId\":\"%s\",\"confidence\":%s,\"reasoning\":\"%s\"}";
  private static final String PLAIN = "Dr. Verma has an opening that suits you.";
  private static final double RULE_CONFIDENCE = 0.75;

  @MockBean LlmGateway gateway;
  @Autowired AiSchedulingStrategy ai;
  @Autowired SchedulingContextFactory contexts;
  @Autowired AppointmentRepository appointments;

  private void takeProviderOneOffline() {
    Provider p1 = providerRepository.findById("PRV-001").orElseThrow();
    p1.setStatus(ProviderStatus.OFFLINE);
    providerRepository.save(p1);
  }

  private List<Recommendation> ask(String appointmentId) {
    Appointment appt = appointments.findById(appointmentId).orElseThrow();
    return ai.recommend(contexts.create(appt, TriggerReason.PROVIDER_OFFLINE));
  }

  private List<Recommendation> recommendFor(String appointmentId, String modelReply) {
    takeProviderOneOffline();
    when(gateway.callLLM(anyString())).thenReturn(modelReply);
    return ask(appointmentId);
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
    addOpenSlot("SLT-X1", "PRV-005", 300); // APT-004's own provider has another opening

    List<Recommendation> r = recommendFor("APT-004", REPLY.formatted("SLT-X1", "", "0.8", PLAIN));

    assertEquals("SLT-X1", r.get(0).slotId());
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
  void aSlotThatIsNotOpenFallsBackToRules() {
    assertFellBackToRules( // SLT-001 is BOOKED, so it is not among the available slots
        recommendFor("APT-001", REPLY.formatted("SLT-001", "PRV-001", "0.9", PLAIN)));
  }

  @Test
  void anOpenSlotOfAnOfflineProviderFallsBackToRules() {
    takeProviderOneOffline();
    addOpenSlot("SLT-X2", "PRV-001", 300); // open, but its provider is offline

    assertFellBackToRules(
        recommendFor("APT-001", REPLY.formatted("SLT-X2", "PRV-001", "0.9", PLAIN)));
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
    takeProviderOneOffline();
    when(gateway.callLLM(anyString())).thenThrow(new AiException("model is down"));

    assertFellBackToRules(ask("APT-001"));
  }
}
