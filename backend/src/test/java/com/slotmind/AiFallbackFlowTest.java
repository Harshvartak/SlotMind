package com.slotmind;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotmind.ai.AiException;
import com.slotmind.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/** strategy=ai but the model is down: the same flow still works through the rule fallback. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:${random.uuid}",
      "slotmind.scheduling.strategy=ai"
    })
class AiFallbackFlowTest extends ApiTestSupport {
  private static final double RULE_CONFIDENCE = 0.75;

  @MockBean LlmGateway gateway;

  @Test
  void suggestionStillCreatedAndAcceptableWhenTheModelIsDown() throws Exception {
    when(gateway.callLLM(anyString())).thenThrow(new AiException("model is down"));
    patchJson("/providers/PRV-001/status", OFFLINE_BODY).andExpect(status().isOk());

    postJson("/appointments/APT-001/suggest", "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recommendedSlotId").value("SLT-005"))
        .andExpect(jsonPath("$.alternateProviderId").value("PRV-004"))
        .andExpect(jsonPath("$.confidence").value(RULE_CONFIDENCE));

    String id = suggestFor("APT-001");
    patchJson("/suggestions/" + id, ACCEPT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.slot.id").value("SLT-005"));
  }
}
