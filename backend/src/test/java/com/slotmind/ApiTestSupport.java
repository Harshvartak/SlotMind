package com.slotmind;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared MockMvc plumbing. Each test runs in a transaction that rolls back, so the brief's seed
 * data (see data.sql) is intact for the next test. Handy facts about that seed: PRV-001 (general,
 * busy) has APT-001 and APT-002; the only open slots are SLT-004 (PRV-002, specialist) and SLT-005
 * (PRV-004, general); APT-003 is pediatric (PRV-003) and APT-004 is PRV-005 (specialist).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
@AutoConfigureMockMvc
@Transactional
abstract class ApiTestSupport {
  protected static final String OFFLINE_BODY = "{\"status\":\"OFFLINE\"}";
  protected static final String ACCEPT_BODY = "{\"status\":\"ACCEPTED\"}";
  protected static final String REJECT_BODY = "{\"status\":\"REJECTED\"}";
  protected static final String CANCEL_BODY = "{\"status\":\"CANCELLED\"}";
  private static final int SLOT_MINUTES = 30;

  @Autowired protected MockMvc mvc;
  @Autowired protected SlotRepository slotRepository;
  @Autowired protected ProviderRepository providerRepository;

  protected ResultActions postJson(String path, String json) throws Exception {
    return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  protected ResultActions patchJson(String path, String json) throws Exception {
    return mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  protected String bookingJson(String patient, String providerId, String slotId) {
    return """
        {"patientName":"%s","type":"ROUTINE","providerId":"%s","slotId":"%s"}"""
        .formatted(patient, providerId, slotId);
  }

  /** Adds an extra OPEN slot, to build a scenario the seed does not contain. */
  protected void addOpenSlot(String id, String providerId, long minutesFromNow) {
    Provider provider = providerRepository.findById(providerId).orElseThrow();
    slotRepository.save(
        Slot.builder()
            .id(id)
            .provider(provider)
            .startTime(LocalDateTime.now().plusMinutes(minutesFromNow))
            .durationMinutes(SLOT_MINUTES)
            .status(SlotStatus.OPEN)
            .build());
  }

  /** Creates an on-demand suggestion and returns its id. */
  protected String suggestFor(String appointmentId) throws Exception {
    String body =
        postJson("/appointments/" + appointmentId + "/suggest", "")
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }
}
