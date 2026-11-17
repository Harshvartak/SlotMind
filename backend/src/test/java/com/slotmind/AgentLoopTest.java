package com.slotmind;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SuggestionRepository;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The async loop. These tests are not transactional on purpose: the agent only runs after the
 * triggering transaction commits, so a rolled-back test transaction would never fire it. Seed
 * reminder: PRV-001 has APT-001 and APT-002; APT-004 is on PRV-005 (SLT-006); PRV-002 and PRV-004
 * have the open slots and no appointments.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:${random.uuid}",
      "slotmind.scheduling.strategy=rule"
    })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AgentLoopTest {
  private static final Duration DEADLINE = Duration.ofSeconds(10);
  private static final Duration QUIET_PERIOD = Duration.ofSeconds(1);

  @Autowired MockMvc mvc;
  @Autowired SuggestionRepository suggestions;
  @Autowired AppointmentRepository appointments;

  private void patchStatus(String path, String status) throws Exception {
    mvc.perform(
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"))
        .andExpect(status().isOk());
  }

  private List<RescheduleSuggestion> pending() {
    return suggestions.findByStatus(SuggestionStatus.PENDING);
  }

  private void awaitPending(int expected) {
    await().atMost(DEADLINE).untilAsserted(() -> assertEquals(expected, pending().size()));
  }

  /** The loop is async, so "nothing happens" means: still nothing after a quiet period. */
  private void assertNoSuggestionsAppear() {
    await()
        .during(QUIET_PERIOD)
        .atMost(DEADLINE)
        .untilAsserted(() -> assertEquals(0, pending().size()));
  }

  /** The newest pending suggestion for an appointment. */
  private RescheduleSuggestion suggestionFor(String appointmentId) {
    return pending().stream()
        .filter(s -> s.getAppointment().getId().equals(appointmentId))
        .max(Comparator.comparing(RescheduleSuggestion::getCreatedAt))
        .orElseThrow();
  }

  @Test
  void providerOfflineQueuesOneSuggestionPerConfirmedAppointmentOfThatProvider() throws Exception {
    patchStatus("/providers/PRV-001/status", "OFFLINE");

    awaitPending(2);

    List<RescheduleSuggestion> created = pending();
    assertTrue(
        created.stream().allMatch(s -> s.getTriggerReason() == TriggerReason.PROVIDER_OFFLINE));
    assertTrue(created.stream().allMatch(s -> s.getRecommendedSlotId().equals("SLT-005")));
    assertEquals(
        List.of("APT-001", "APT-002"),
        created.stream().map(s -> s.getAppointment().getId()).sorted().toList());
    assertEquals(
        AppointmentStatus.RESCHEDULE_PENDING,
        appointments.findById("APT-001").orElseThrow().getStatus());
    assertEquals(
        AppointmentStatus.CONFIRMED, appointments.findById("APT-003").orElseThrow().getStatus());
  }

  @Test
  void slotCancelledQueuesASuggestionForThatSlotsAppointmentOnly() throws Exception {
    patchStatus("/slots/SLT-006/status", "CANCELLED"); // booked by APT-004 with PRV-005

    awaitPending(1);

    RescheduleSuggestion s = pending().get(0);
    assertEquals("APT-004", s.getAppointment().getId());
    assertEquals(TriggerReason.SLOT_CANCELLED, s.getTriggerReason());
    assertEquals("SLT-004", s.getRecommendedSlotId()); // the other specialist's open slot
    assertEquals(Optional.of("PRV-002"), s.alternateProvider());
  }

  @Test
  void repeatedOfflineEventsDoNotCreateDuplicatePendingSuggestions() throws Exception {
    patchStatus("/providers/PRV-001/status", "OFFLINE");
    awaitPending(2);

    patchStatus("/providers/PRV-001/status", "OFFLINE");

    await()
        .during(QUIET_PERIOD)
        .atMost(DEADLINE)
        .untilAsserted(() -> assertEquals(2, pending().size()));
  }

  @Test
  void cancellingTheNewSlotOfARescheduledAppointmentReschedulesItAgain() throws Exception {
    patchStatus("/providers/PRV-001/status", "OFFLINE");
    awaitPending(2);
    RescheduleSuggestion first = suggestionFor("APT-001");
    mvc.perform(
            patch("/suggestions/" + first.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
        .andExpect(status().isOk()); // APT-001 now holds SLT-005 with PRV-004, SLT-001 reopens
    patchStatus("/providers/PRV-001/status", "AVAILABLE"); // so SLT-001 is a valid target again

    patchStatus("/slots/SLT-005/status", "CANCELLED");

    await()
        .atMost(DEADLINE)
        .untilAsserted(
            () -> {
              RescheduleSuggestion again = suggestionFor("APT-001");
              assertEquals(TriggerReason.SLOT_CANCELLED, again.getTriggerReason());
              assertEquals("SLT-001", again.getRecommendedSlotId());
            });
  }

  @Test
  void appointmentsAlreadyAwaitingADecisionAreLeftAlone() throws Exception {
    mvc.perform(post("/appointments/APT-001/suggest")).andExpect(status().isCreated());
    awaitPending(1); // a manual suggestion; APT-001 is now RESCHEDULE_PENDING

    patchStatus("/providers/PRV-001/status", "OFFLINE");

    awaitPending(2); // APT-002 gets one; APT-001 keeps only its manual one
    assertEquals(
        1, pending().stream().filter(s -> s.getAppointment().getId().equals("APT-001")).count());
  }

  @Test
  void offlineProviderWithNoConfirmedAppointmentsQueuesNothing() throws Exception {
    patchStatus("/providers/PRV-004/status", "OFFLINE"); // only has an open slot

    assertNoSuggestionsAppear();
  }

  @Test
  void cancellingAnOpenSlotQueuesNothing() throws Exception {
    patchStatus("/slots/SLT-004/status", "CANCELLED"); // nobody has booked it

    assertNoSuggestionsAppear();
  }

  @Test
  void goingBackOnlineQueuesNothing() throws Exception {
    patchStatus("/providers/PRV-001/status", "AVAILABLE");

    assertNoSuggestionsAppear();
  }
}
