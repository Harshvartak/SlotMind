package com.slotmind.ai;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code slotmind.llm.*} in application.yml (secrets come from env vars). Text values
 * are trimmed and never null, so a stray space in a .env file cannot break a header or URL.
 *
 * @param headers extra HTTP headers sent with every call, e.g. a gateway's required "product"
 */
@ConfigurationProperties("slotmind.llm")
public record LlmProperties(
    String provider,
    String model,
    String baseUrl,
    String apiKey,
    int timeoutMs,
    Map<String, String> headers) {

  public LlmProperties {
    provider = clean(provider);
    model = clean(model);
    baseUrl = clean(baseUrl);
    apiKey = clean(apiKey);
    headers =
        Optional.ofNullable(headers).orElse(Map.of()).entrySet().stream()
            .filter(e -> !clean(e.getValue()).isEmpty())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> clean(e.getValue())));
  }

  private static String clean(String value) {
    return Optional.ofNullable(value).orElse("").strip();
  }
}
