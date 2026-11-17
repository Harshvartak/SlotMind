package com.slotmind;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** strategy=ai with the default Mock gateway: the model's confidence and reasoning are used. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:${random.uuid}",
      "slotmind.scheduling.strategy=ai"
    })
class AiFlowTest extends ApiTestSupport {
  private static final double MOCK_CONFIDENCE = 0.88;

  @Test
  void offlineProviderGetsAnAiSuggestionThatCanBeAccepted() throws Exception {
    patchJson("/providers/PRV-001/status", OFFLINE_BODY).andExpect(status().isOk());

    String body =
        postJson("/appointments/APT-001/suggest", "")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recommendedSlotId").value("SLT-005"))
            .andExpect(jsonPath("$.alternateProviderId").value("PRV-004"))
            .andExpect(jsonPath("$.confidence").value(MOCK_CONFIDENCE))
            .andExpect(jsonPath("$.reasoning").value(not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
    patchJson("/suggestions/" + id, ACCEPT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.status").value("RESCHEDULED"));
  }
}
