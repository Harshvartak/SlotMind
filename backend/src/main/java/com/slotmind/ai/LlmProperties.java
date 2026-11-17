package com.slotmind.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code slotmind.llm.*} in application.yml (the api key comes from an env var). */
@ConfigurationProperties("slotmind.llm")
public record LlmProperties(
    String provider, String model, String baseUrl, String apiKey, int timeoutMs) {}
