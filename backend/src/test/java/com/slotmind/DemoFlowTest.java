package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The hackathon demo path over real HTTP (random port, committed transactions, nothing mocked):
 * PRV-001 goes offline, both of its appointments get a suggestion, accept one, the other loses the
 * race for the single open slot and the desk rejects it.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:${random.uuid}",
      "slotmind.scheduling.strategy=rule"
    })
@DirtiesContext
class DemoFlowTest {
  private static final String OFFLINE_BODY = "{\"status\":\"OFFLINE\"}";
  private static final String ACCEPT_BODY = "{\"status\":\"ACCEPTED\"}";
  private static final String REJECT_BODY = "{\"status\":\"REJECTED\"}";
  private static final String SHARED_SLOT = "SLT-005";

  @LocalServerPort int port;

  private final HttpClient client = HttpClient.newHttpClient();

  private HttpResponse<String> call(String method, String path, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(
                method, body.isEmpty() ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  private <T> T json(HttpResponse<String> response, String path) {
    return JsonPath.read(response.body(), path);
  }

  @Test
  void providerGoesOfflineThenFrontDeskAcceptsOneAndRejectsTheOther() throws Exception {
    assertEquals(200, call("GET", "/appointments?status=CONFIRMED", "").statusCode());
    assertEquals(200, call("PATCH", "/providers/PRV-001/status", OFFLINE_BODY).statusCode());

    String first = suggestAndCheck("APT-001");
    String second = suggestAndCheck("APT-002");
    assertPendingState();

    acceptFirst(first);
    assertEquals(409, call("PATCH", "/suggestions/" + second, ACCEPT_BODY).statusCode());
    assertEquals(200, call("PATCH", "/suggestions/" + second, REJECT_BODY).statusCode());
    assertEquals("CONFIRMED", appointmentStatus("APT-002"));
  }

  private String suggestAndCheck(String appointmentId) throws Exception {
    HttpResponse<String> r = call("POST", "/appointments/" + appointmentId + "/suggest", "");
    assertEquals(201, r.statusCode());
    assertEquals(SHARED_SLOT, json(r, "$.recommendedSlotId"));
    assertEquals("PRV-004", json(r, "$.alternateProviderId"));
    assertEquals(
        "INITIAL", json(r, "$.triggerReason")); // manual /suggest; the loop adds the others
    return json(r, "$.id");
  }

  private void assertPendingState() throws Exception {
    List<String> pending = json(call("GET", "/suggestions?status=PENDING", ""), "$[*].id");
    List<String> waiting =
        json(call("GET", "/appointments?status=RESCHEDULE_PENDING", ""), "$[*].id");
    assertEquals(2, pending.size());
    assertEquals(List.of("APT-001", "APT-002"), waiting.stream().sorted().toList());
  }

  private void acceptFirst(String suggestionId) throws Exception {
    HttpResponse<String> r = call("PATCH", "/suggestions/" + suggestionId, ACCEPT_BODY);
    assertEquals(200, r.statusCode());
    assertEquals("RESCHEDULED", json(r, "$.appointment.status"));
    assertEquals(SHARED_SLOT, json(r, "$.appointment.slot.id"));
    assertEquals("PRV-004", json(r, "$.appointment.provider.id"));

    HttpResponse<String> slots = call("GET", "/slots", "");
    assertEquals("BOOKED", slotStatus(slots, SHARED_SLOT));
    assertEquals("OPEN", slotStatus(slots, "SLT-001"));
  }

  private String slotStatus(HttpResponse<String> slots, String id) {
    List<String> status = json(slots, "$[?(@.id=='" + id + "')].status");
    return status.get(0);
  }

  private String appointmentStatus(String id) throws Exception {
    List<String> status = json(call("GET", "/appointments", ""), "$[?(@.id=='" + id + "')].status");
    return status.get(0);
  }
}
