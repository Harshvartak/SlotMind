package com.slotmind;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The hackathon demo over real HTTP (random port, committed transactions, nothing mocked): PRV-001
 * goes offline, suggestions for both of its appointments appear on their own, the desk accepts one,
 * the other loses the race for the single open slot, and the desk rejects it.
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
  private static final Duration LOOP_DEADLINE = Duration.ofSeconds(10);

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
    assertEquals(200, call("PATCH", "/providers/PRV-001/status", OFFLINE_BODY).statusCode());

    HttpResponse<String> pending = awaitTwoPendingSuggestions();
    assertSuggestionsWerePlacedByTheLoop(pending);
    List<String> suggestionIds = json(pending, "$[*].id");
    List<String> appointmentIds = json(pending, "$[*].appointment.id");

    acceptFirst(suggestionIds.get(0));
    assertEquals(
        409, call("PATCH", "/suggestions/" + suggestionIds.get(1), ACCEPT_BODY).statusCode());
    assertEquals(
        200, call("PATCH", "/suggestions/" + suggestionIds.get(1), REJECT_BODY).statusCode());

    List<String> confirmed = json(call("GET", "/appointments?status=CONFIRMED", ""), "$[*].id");
    assertTrue(confirmed.contains(appointmentIds.get(1)));
  }

  /** No manual /suggest call anywhere: the async loop must create these by itself. */
  private HttpResponse<String> awaitTwoPendingSuggestions() {
    AtomicReference<HttpResponse<String>> latest = new AtomicReference<>();
    await()
        .atMost(LOOP_DEADLINE)
        .untilAsserted(
            () -> {
              latest.set(call("GET", "/suggestions?status=PENDING", ""));
              List<String> ids = json(latest.get(), "$[*].id");
              assertEquals(2, ids.size());
            });
    return latest.get();
  }

  private void assertSuggestionsWerePlacedByTheLoop(HttpResponse<String> pending) throws Exception {
    assertEquals(
        List.of("PROVIDER_OFFLINE", "PROVIDER_OFFLINE"), json(pending, "$[*].triggerReason"));
    assertEquals(List.of(SHARED_SLOT, SHARED_SLOT), json(pending, "$[*].recommendedSlotId"));
    assertEquals(List.of("PRV-004", "PRV-004"), json(pending, "$[*].alternateProviderId"));
    List<String> waiting =
        json(call("GET", "/appointments?status=RESCHEDULE_PENDING", ""), "$[*].id");
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
}
