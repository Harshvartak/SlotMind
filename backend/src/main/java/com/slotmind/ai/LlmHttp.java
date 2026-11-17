package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

/** The JSON-over-HTTP plumbing every gateway shares: post a body, pull one text field out. */
final class LlmHttp {
  private static final int HTTP_OK = 200;
  private static final int ERROR_SNIPPET = 200;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper;
  private final int timeoutMs;

  LlmHttp(ObjectMapper mapper, int timeoutMs) {
    this.mapper = mapper;
    this.timeoutMs = timeoutMs;
  }

  /** Posts {@code body} as JSON and returns the text at {@code textPointer} (a JSON pointer). */
  String postForText(String url, Map<String, String> headers, Object body, String textPointer) {
    try {
      HttpResponse<String> response =
          http.send(request(url, headers, body), BodyHandlers.ofString());
      if (response.statusCode() != HTTP_OK) {
        throw new AiException(
            "LLM answered HTTP %d: %s".formatted(response.statusCode(), snippet(response.body())));
      }
      return extractText(response.body(), textPointer);
    } catch (IOException e) {
      throw new AiException("LLM call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AiException("LLM call interrupted", e);
    } catch (IllegalArgumentException e) {
      throw new AiException("LLM url is invalid: " + url, e);
    }
  }

  private HttpRequest request(String url, Map<String, String> headers, Object body)
      throws IOException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(mapper.writeValueAsString(body)));
    headers.forEach(builder::header);
    return builder.build();
  }

  private String extractText(String responseBody, String textPointer) throws IOException {
    String text = mapper.readTree(responseBody).at(textPointer).asText("");
    if (text.isBlank()) {
      throw new AiException("LLM returned no text");
    }
    return text;
  }

  private static String snippet(String text) {
    return text.length() <= ERROR_SNIPPET ? text : text.substring(0, ERROR_SNIPPET);
  }
}
