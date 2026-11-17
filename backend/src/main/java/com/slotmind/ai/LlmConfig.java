package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the gateway from {@code slotmind.llm.provider}; an unknown value fails at startup. */
@Configuration
public class LlmConfig {
  @Bean
  public LlmGateway llmGateway(LlmProperties props, ObjectMapper mapper) {
    String provider = Optional.ofNullable(props.provider()).orElse(MockGateway.NAME);
    return switch (provider) {
      case MockGateway.NAME -> new MockGateway();
      case GeminiGateway.NAME -> new GeminiGateway(props, mapper);
      default ->
          throw new IllegalStateException(
              "Unknown slotmind.llm.provider '%s'; use mock or gemini".formatted(provider));
    };
  }
}
