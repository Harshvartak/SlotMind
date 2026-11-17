package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini over REST. The key travels in a header, never in the URL, so it cannot leak into
 * access logs.
 */
public class GeminiGateway implements LlmGateway {
  public static final String NAME = "gemini";
  private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
  private static final String PATH = "/v1beta/models/%s:generateContent";
  private static final String KEY_HEADER = "x-goog-api-key";
  private static final String REPLY_TEXT = "/candidates/0/content/parts/0/text";

  private final LlmProperties props;
  private final LlmHttp http;

  public GeminiGateway(LlmProperties props, ObjectMapper mapper) {
    this.props = props;
    this.http = new LlmHttp(mapper, props.timeoutMs());
  }

  @Override
  public String callLLM(String prompt) {
    requireConfigured();
    String base = props.baseUrl().isEmpty() ? DEFAULT_BASE_URL : props.baseUrl();
    return http.postForText(
        base + PATH.formatted(props.model()),
        Map.of(KEY_HEADER, props.apiKey()),
        body(prompt),
        REPLY_TEXT);
  }

  private void requireConfigured() {
    if (props.apiKey().isEmpty()) {
      throw new AiException("slotmind.llm.api-key is not set");
    }
    if (props.model().isEmpty()) {
      throw new AiException("slotmind.llm.model is not set");
    }
  }

  private static Map<String, Object> body(String prompt) {
    return Map.of(
        "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
        "generationConfig", Map.of("responseMimeType", "application/json"));
  }
}
