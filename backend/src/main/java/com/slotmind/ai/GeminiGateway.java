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
import java.util.List;
import java.util.Map;

/**
 * Google Gemini over REST, using only the JDK HTTP client. The key travels in a header, never in
 * the URL, so it cannot leak into access logs.
 */
public class GeminiGateway implements LlmGateway {
  public static final String NAME = "gemini";
  private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
  private static final String PATH = "/v1beta/models/%s:generateContent";
  private static final String KEY_HEADER = "x-goog-api-key";
  private static final String REPLY_TEXT = "/candidates/0/content/parts/0/text";
  private static final int HTTP_OK = 200;

  private final LlmProperties props;
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newHttpClient();

  public GeminiGateway(LlmProperties props, ObjectMapper mapper) {
    this.props = props;
    this.mapper = mapper;
  }

  @Override
  public String callLLM(String prompt) {
    requireConfigured();
    try {
      HttpResponse<String> response = http.send(request(prompt), BodyHandlers.ofString());
      if (response.statusCode() != HTTP_OK) {
        throw new AiException("Gemini answered HTTP " + response.statusCode());
      }
      return extractText(response.body());
    } catch (IOException e) {
      throw new AiException("Gemini call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AiException("Gemini call interrupted", e);
    }
  }

  private void requireConfigured() {
    if (props.apiKey() == null || props.apiKey().isBlank()) {
      throw new AiException("slotmind.llm.api-key is not set");
    }
    if (props.model() == null || props.model().isBlank()) {
      throw new AiException("slotmind.llm.model is not set");
    }
  }

  private HttpRequest request(String prompt) throws IOException {
    String base =
        props.baseUrl() == null || props.baseUrl().isBlank() ? DEFAULT_BASE_URL : props.baseUrl();
    return HttpRequest.newBuilder(URI.create(base + PATH.formatted(props.model())))
        .timeout(Duration.ofMillis(props.timeoutMs()))
        .header("Content-Type", "application/json")
        .header(KEY_HEADER, props.apiKey())
        .POST(BodyPublishers.ofString(body(prompt)))
        .build();
  }

  private String body(String prompt) throws IOException {
    return mapper.writeValueAsString(
        Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of("responseMimeType", "application/json")));
  }

  private String extractText(String responseBody) throws IOException {
    String text = mapper.readTree(responseBody).at(REPLY_TEXT).asText("");
    if (text.isBlank()) {
      throw new AiException("Gemini returned no text");
    }
    return text;
  }
}
