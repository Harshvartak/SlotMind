package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Any server speaking the OpenAI chat-completions format: LiteLLM proxies, Azure/OpenAI gateways,
 * Groq, Ollama and so on. {@code base-url} may be the API root (".../v1") or the full
 * ".../chat/completions" endpoint. Extra headers (e.g. a gateway's required "product") come from
 * {@code slotmind.llm.headers}; the api key is optional so local servers work without one.
 */
public class OpenAiCompatibleGateway implements LlmGateway {
  public static final String NAME = "openai_compatible";

  /** Spelling accepted so an existing .env that says "openapi_compact" keeps working. */
  public static final String ALIAS = "openapi_compact";

  private static final String ENDPOINT = "/chat/completions";
  private static final String REPLY_TEXT = "/choices/0/message/content";

  private final LlmProperties props;
  private final LlmHttp http;

  public OpenAiCompatibleGateway(LlmProperties props, ObjectMapper mapper) {
    this.props = props;
    this.http = new LlmHttp(mapper, props.timeoutMs());
  }

  @Override
  public String callLLM(String prompt) {
    requireConfigured();
    return http.postForText(endpoint(), headers(), body(prompt), REPLY_TEXT);
  }

  private void requireConfigured() {
    if (props.baseUrl().isEmpty()) {
      throw new AiException("slotmind.llm.base-url is not set");
    }
    if (props.model().isEmpty()) {
      throw new AiException("slotmind.llm.model is not set");
    }
  }

  private String endpoint() {
    String base = props.baseUrl().replaceAll("/+$", "");
    return base.endsWith(ENDPOINT) ? base : base + ENDPOINT;
  }

  private Map<String, String> headers() {
    Map<String, String> all = new LinkedHashMap<>(props.headers());
    if (!props.apiKey().isEmpty()) {
      all.put("Authorization", "Bearer " + props.apiKey());
    }
    return all;
  }

  private Map<String, Object> body(String prompt) {
    return Map.of(
        "model", props.model(),
        "messages", List.of(Map.of("role", "user", "content", prompt)),
        "response_format", Map.of("type", "json_object"));
  }
}
