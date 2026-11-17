package com.slotmind;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ProviderSlotApiTest extends ApiTestSupport {
  private static final String INVALID_STATUS_BODY = "{\"status\":\"ON_HOLIDAY\"}";
  private static final String BOOKED_BODY = "{\"status\":\"BOOKED\"}";
  private static final String OPEN_BODY = "{\"status\":\"OPEN\"}";

  @Test
  void listsSeededProvidersAndSlots() throws Exception {
    mvc.perform(get("/providers")).andExpect(jsonPath("$", hasSize(5)));
    mvc.perform(get("/slots")).andExpect(jsonPath("$", hasSize(6)));
  }

  @Test
  void providerStatusCanBeChanged() throws Exception {
    patchJson("/providers/PRV-001/status", OFFLINE_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OFFLINE"));
  }

  @Test
  void unknownProviderIsNotFound() throws Exception {
    patchJson("/providers/PRV-999/status", OFFLINE_BODY).andExpect(status().isNotFound());
  }

  @Test
  void invalidProviderStatusIsBadRequest() throws Exception {
    patchJson("/providers/PRV-001/status", INVALID_STATUS_BODY).andExpect(status().isBadRequest());
  }

  @Test
  void slotStatusCanBeCancelled() throws Exception {
    patchJson("/slots/SLT-004/status", CANCEL_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void cancellingAnAlreadyCancelledSlotIsHarmless() throws Exception {
    patchJson("/slots/SLT-004/status", CANCEL_BODY).andExpect(status().isOk());

    patchJson("/slots/SLT-004/status", CANCEL_BODY).andExpect(status().isOk());
  }

  @Test
  void aCancelledSlotCannotBeBookedDirectly() throws Exception {
    patchJson("/slots/SLT-004/status", CANCEL_BODY).andExpect(status().isOk());

    patchJson("/slots/SLT-004/status", BOOKED_BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Slot SLT-004 cannot go from CANCELLED to BOOKED"));
  }

  @Test
  void aCancelledSlotCanBeReopened() throws Exception {
    patchJson("/slots/SLT-004/status", CANCEL_BODY).andExpect(status().isOk());

    patchJson("/slots/SLT-004/status", OPEN_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void unknownSlotIsNotFound() throws Exception {
    patchJson("/slots/SLT-999/status", CANCEL_BODY).andExpect(status().isNotFound());
  }

  @Test
  void missingSlotStatusFailsValidation() throws Exception {
    patchJson("/slots/SLT-004/status", "{}").andExpect(status().isBadRequest());
  }
}
