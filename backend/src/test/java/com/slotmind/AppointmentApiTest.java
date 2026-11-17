package com.slotmind;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AppointmentApiTest extends ApiTestSupport {
  private static final String OPEN_SPECIALIST_SLOT = "SLT-004";
  private static final String SPECIALIST = "PRV-002";

  @Test
  void bookingMarksSlotBookedAndConfirmsAppointment() throws Exception {
    postJson("/appointments", bookingJson("Ana Lopez", SPECIALIST, OPEN_SPECIALIST_SLOT))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.slot.status").value("BOOKED"))
        .andExpect(jsonPath("$.provider.id").value(SPECIALIST));
  }

  @Test
  void bookingAnAlreadyBookedSlotIsRejected() throws Exception {
    postJson("/appointments", bookingJson("Ana Lopez", "PRV-003", "SLT-003"))
        .andExpect(status().isConflict());
  }

  @Test
  void bookingASlotOfAnotherProviderIsRejected() throws Exception {
    postJson("/appointments", bookingJson("Ana Lopez", "PRV-001", OPEN_SPECIALIST_SLOT))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bookingWithAnOfflineProviderIsRejected() throws Exception {
    patchJson("/providers/PRV-004/status", OFFLINE_BODY).andExpect(status().isOk());

    postJson("/appointments", bookingJson("Ana Lopez", "PRV-004", "SLT-005"))
        .andExpect(status().isConflict());
  }

  @Test
  void bookingWithUnknownSlotIsNotFound() throws Exception {
    postJson("/appointments", bookingJson("Ana Lopez", SPECIALIST, "SLT-999"))
        .andExpect(status().isNotFound());
  }

  @Test
  void bookingWithBlankPatientNameFailsValidation() throws Exception {
    postJson("/appointments", bookingJson("", SPECIALIST, OPEN_SPECIALIST_SLOT))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listFiltersByStatus() throws Exception {
    mvc.perform(get("/appointments")).andExpect(jsonPath("$", hasSize(4)));
    mvc.perform(get("/appointments").param("status", "CONFIRMED"))
        .andExpect(jsonPath("$", hasSize(4)));
    mvc.perform(get("/appointments").param("status", "RESCHEDULED"))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void suggestKeepsTheSameProviderWhenTheyHaveAnotherOpenSlot() throws Exception {
    addOpenSlot("SLT-X1", "PRV-001", 300);

    postJson("/appointments/APT-001/suggest", "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recommendedSlotId").value("SLT-X1"))
        .andExpect(jsonPath("$.alternateProviderId").value(nullValue()))
        .andExpect(jsonPath("$.triggerReason").value("INITIAL"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.appointment.status").value("RESCHEDULE_PENDING"));
  }

  @Test
  void suggestOffersAnAlternateProviderWhenTheirProviderIsOffline() throws Exception {
    patchJson("/providers/PRV-001/status", OFFLINE_BODY).andExpect(status().isOk());

    postJson("/appointments/APT-001/suggest", "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recommendedSlotId").value("SLT-005"))
        .andExpect(jsonPath("$.alternateProviderId").value("PRV-004"));
  }

  @Test
  void suggestTwiceReturnsTheSamePendingSuggestion() throws Exception {
    String first = suggestFor("APT-004");
    String second = suggestFor("APT-004");

    assertEquals(first, second);
  }

  @Test
  void suggestForUnknownAppointmentIsNotFound() throws Exception {
    postJson("/appointments/APT-999/suggest", "").andExpect(status().isNotFound());
  }

  @Test
  void suggestWithNoFittingSlotIsUnprocessable() throws Exception {
    // APT-003 is pediatric (PRV-003); the open slots belong to a specialist and a general doctor
    postJson("/appointments/APT-003/suggest", "").andExpect(status().isUnprocessableEntity());
  }
}
