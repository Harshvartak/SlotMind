package com.slotmind;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Shared MockMvc plumbing. Each test runs in a transaction that rolls back, so seed data stays. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
@AutoConfigureMockMvc
@Transactional
abstract class ApiTestSupport {
  protected static final String OFFLINE_BODY = "{\"status\":\"OFFLINE\"}";
  protected static final String ACCEPT_BODY = "{\"status\":\"ACCEPTED\"}";
  protected static final String REJECT_BODY = "{\"status\":\"REJECTED\"}";

  @Autowired protected MockMvc mvc;

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
