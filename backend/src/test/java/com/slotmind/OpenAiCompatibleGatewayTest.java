package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.ai.AiException;
import com.slotmind.ai.LlmProperties;
import com.slotmind.ai.OpenAiCompatibleGateway;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the real gateway against a local stub server, so no network or key is needed. */
class OpenAiCompatibleGatewayTest {
  private static final String KEY = "test-key";
  private static final String MODEL = "test-model";
  private static final String ENDPOINT = "/v1/chat/completions";
  private static final int TIMEOUT_MS = 2000;
  private static final String OK_BODY =
      "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"slotId\\\":\\\"S1\\\"}\"}}]}";

  private HttpServer server;
  private int status;
  private String responseBody;
  private final AtomicReference<String> seenPath = new AtomicReference<>("");
  private final AtomicReference<String> seenAuth = new AtomicReference<>("");
  private final AtomicReference<String> seenProduct = new AtomicReference<>("");
  private final AtomicReference<String> seenBody = new AtomicReference<>("");

  @BeforeEach
  void startStub() throws IOException {
    status = 200;
    responseBody = OK_BODY;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          seenPath.set(exchange.getRequestURI().getPath());
          seenAuth.set(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
          seenProduct.set(String.valueOf(exchange.getRequestHeaders().getFirst("product")));
          seenBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  private String root() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private OpenAiCompatibleGateway gateway(String baseUrl, String key, Map<String, String> headers) {
    return new OpenAiCompatibleGateway(
        new LlmProperties("openai_compatible", MODEL, baseUrl, key, TIMEOUT_MS, headers),
        new ObjectMapper());
  }

  @Test
  void sendsModelPromptJsonModeKeyAndExtraHeaderAndReturnsTheContent() throws IOException {
    String text = gateway(root() + "/v1", KEY, Map.of("product", "pc1")).callLLM("pick a slot");

    assertEquals("{\"slotId\":\"S1\"}", text);
    assertEquals(ENDPOINT, seenPath.get());
    assertEquals("Bearer " + KEY, seenAuth.get());
    assertEquals("pc1", seenProduct.get());
    JsonNode sent = new ObjectMapper().readTree(seenBody.get());
    assertEquals(MODEL, sent.get("model").asText());
    assertEquals("pick a slot", sent.at("/messages/0/content").asText());
    assertEquals("json_object", sent.at("/response_format/type").asText());
  }

  @Test
  void aFullEndpointUrlIsUsedAsIs() {
    gateway(root() + ENDPOINT, KEY, Map.of()).callLLM("x");

    assertEquals(ENDPOINT, seenPath.get());
  }

  @Test
  void whitespaceAroundConfigValuesIsIgnored() {
    gateway("  " + root() + ENDPOINT + "  ", "  " + KEY + "  ", Map.of("product", " pc1 "))
        .callLLM("x");

    assertEquals("Bearer " + KEY, seenAuth.get());
    assertEquals("pc1", seenProduct.get());
  }

  @Test
  void blankKeyAndBlankHeaderAreNotSent() {
    gateway(root() + ENDPOINT, "", Map.of("product", "")).callLLM("x");

    assertEquals("null", seenAuth.get());
    assertEquals("null", seenProduct.get());
  }

  @Test
  void nonOkStatusIsAnAiExceptionThatKeepsTheServersReason() {
    status = 400;
    responseBody = "{\"error\":{\"message\":\"Missing required request tags: product\"}}";

    AiException e =
        assertThrows(
            AiException.class, () -> gateway(root() + ENDPOINT, KEY, Map.of()).callLLM("x"));

    assertTrue(e.getMessage().contains("400"));
    assertTrue(e.getMessage().contains("Missing required request tags"));
  }

  @Test
  void replyWithoutContentIsAnAiException() {
    responseBody = "{\"choices\":[]}";

    assertThrows(AiException.class, () -> gateway(root() + ENDPOINT, KEY, Map.of()).callLLM("x"));
  }

  @Test
  void missingBaseUrlOrModelFailsBeforeAnyCall() {
    assertThrows(AiException.class, () -> gateway("", KEY, Map.of()).callLLM("x"));
    OpenAiCompatibleGateway noModel =
        new OpenAiCompatibleGateway(
            new LlmProperties("openai_compatible", "", root(), KEY, TIMEOUT_MS, Map.of()),
            new ObjectMapper());
    assertThrows(AiException.class, () -> noModel.callLLM("x"));
    assertEquals("", seenPath.get()); // neither call reached the server
  }
}
