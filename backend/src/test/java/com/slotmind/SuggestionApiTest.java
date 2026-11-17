package com.slotmind;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** APT-004 (PRV-005, specialist, SLT-006) is the easy one to move: SLT-004 is open on PRV-002. */
class SuggestionApiTest extends ApiTestSupport {
  private static final String SLOT_STATUS_PATH = "$[?(@.id=='%s')].status";

  @Test
  void acceptingMovesTheAppointmentAndSwapsBothSlots() throws Exception {
    String id = suggestFor("APT-004");

    patchJson("/suggestions/" + id, ACCEPT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.appointment.status").value("RESCHEDULED"))
        .andExpect(jsonPath("$.appointment.slot.id").value("SLT-004"));

    mvc.perform(get("/slots"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-004")).value("BOOKED"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-006")).value("OPEN"));
  }

  @Test
  void acceptingAnAlternateProviderSuggestionChangesTheProvider() throws Exception {
    patchJson("/providers/PRV-001/status", OFFLINE_BODY).andExpect(status().isOk());
    String id = suggestFor("APT-001");

    patchJson("/suggestions/" + id, ACCEPT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.provider.id").value("PRV-004"));
  }

  @Test
  void rejectingRestoresTheAppointmentAndLeavesSlotsUntouched() throws Exception {
    String id = suggestFor("APT-004");

    patchJson("/suggestions/" + id, REJECT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"));

    mvc.perform(get("/slots"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-004")).value("OPEN"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-006")).value("BOOKED"));
  }

  @Test
  void decidingTwiceIsAConflict() throws Exception {
    String id = suggestFor("APT-004");
    patchJson("/suggestions/" + id, ACCEPT_BODY).andExpect(status().isOk());

    patchJson("/suggestions/" + id, ACCEPT_BODY).andExpect(status().isConflict());
  }

  @Test
  void acceptingWhenTheSlotWasTakenInTheMeantimeIsAConflict() throws Exception {
    String id = suggestFor("APT-004");
    postJson("/appointments", bookingJson("Ana Lopez", "PRV-002", "SLT-004"))
        .andExpect(status().isCreated());

    patchJson("/suggestions/" + id, ACCEPT_BODY).andExpect(status().isConflict());
  }

  @Test
  void acceptingWhenTheTargetProviderWentOfflineIsAConflict() throws Exception {
    String id = suggestFor("APT-004"); // recommends SLT-004 on PRV-002
    patchJson("/providers/PRV-002/status", OFFLINE_BODY).andExpect(status().isOk());

    patchJson("/suggestions/" + id, ACCEPT_BODY).andExpect(status().isConflict());

    mvc.perform(get("/slots"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-004")).value("OPEN"));
  }

  @Test
  void acceptingAfterASlotCancellationLeavesTheCancelledSlotCancelled() throws Exception {
    patchJson("/slots/SLT-006/status", CANCEL_BODY).andExpect(status().isOk());
    String id = suggestFor("APT-004");

    patchJson("/suggestions/" + id, ACCEPT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.slot.id").value("SLT-004"));

    mvc.perform(get("/slots"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-006")).value("CANCELLED"))
        .andExpect(jsonPath(SLOT_STATUS_PATH.formatted("SLT-004")).value("BOOKED"));
  }

  @Test
  void anAppointmentThatWasAlreadyRescheduledCanBeRescheduledAgain() throws Exception {
    String id = suggestFor("APT-004"); // SLT-006 to SLT-004
    patchJson("/suggestions/" + id, ACCEPT_BODY).andExpect(status().isOk());

    postJson("/appointments/APT-004/suggest", "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recommendedSlotId").value("SLT-006")); // the slot it just left
  }

  @Test
  void pendingIsNotAValidDecision() throws Exception {
    String id = suggestFor("APT-004");

    patchJson("/suggestions/" + id, "{\"status\":\"PENDING\"}").andExpect(status().isBadRequest());
  }

  @Test
  void unknownSuggestionIsNotFound() throws Exception {
    patchJson("/suggestions/SUG-NOPE", ACCEPT_BODY).andExpect(status().isNotFound());
  }

  @Test
  void listFiltersByStatus() throws Exception {
    suggestFor("APT-004");

    mvc.perform(get("/suggestions").param("status", "PENDING"))
        .andExpect(jsonPath("$", hasSize(1)));
    mvc.perform(get("/suggestions").param("status", "ACCEPTED"))
        .andExpect(jsonPath("$", hasSize(0)));
  }
}
