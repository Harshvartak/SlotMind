package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.ai.AiException;
import com.slotmind.ai.GeminiGateway;
import com.slotmind.ai.LlmProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the real gateway against a local stub server, so no network or key is needed. */
class GeminiGatewayTest {
  private static final String KEY = "test-key";
  private static final String MODEL = "test-model";
  private static final int TIMEOUT_MS = 2000;
  private static final String OK_BODY =
      "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"slotId\\\":\\\"SLT-005\\\"}\"}]}}]}";

  private HttpServer server;
  private int status;
  private String responseBody;
  private final AtomicReference<String> seenKey = new AtomicReference<>("");
  private final AtomicReference<String> seenPath = new AtomicReference<>("");
  private final AtomicReference<String> seenQuery = new AtomicReference<>("");

  @BeforeEach
  void startStub() throws IOException {
    status = 200;
    responseBody = OK_BODY;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          seenKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
          seenPath.set(exchange.getRequestURI().getPath());
          seenQuery.set(String.valueOf(exchange.getRequestURI().getQuery()));
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

  private GeminiGateway gateway(String key, String model) {
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    return new GeminiGateway(
        new LlmProperties("gemini", model, url, key, TIMEOUT_MS), new ObjectMapper());
  }

  @Test
  void returnsTheModelTextAndSendsTheKeyInAHeaderNotTheUrl() {
    String text = gateway(KEY, MODEL).callLLM("pick a slot");

    assertEquals("{\"slotId\":\"SLT-005\"}", text);
    assertEquals(KEY, seenKey.get());
    assertEquals("/v1beta/models/test-model:generateContent", seenPath.get());
    assertEquals("null", seenQuery.get());
  }

  @Test
  void nonOkStatusIsAnAiException() {
    status = 500;

    assertTrue(
        assertThrows(AiException.class, () -> gateway(KEY, MODEL).callLLM("x"))
            .getMessage()
            .contains("500"));
  }

  @Test
  void replyWithoutTextIsAnAiException() {
    responseBody = "{\"candidates\":[]}";

    assertThrows(AiException.class, () -> gateway(KEY, MODEL).callLLM("x"));
  }

  @Test
  void missingKeyOrModelFailsBeforeAnyCall() {
    assertThrows(AiException.class, () -> gateway("", MODEL).callLLM("x"));
    assertThrows(AiException.class, () -> gateway(KEY, "").callLLM("x"));
  }
}
