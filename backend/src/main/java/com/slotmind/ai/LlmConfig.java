package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the gateway from {@code slotmind.llm.provider}; an unknown value fails at startup. */
@Configuration
public class LlmConfig {
  @Bean
  public LlmGateway llmGateway(LlmProperties props, ObjectMapper mapper) {
    String provider = props.provider().isEmpty() ? MockGateway.NAME : props.provider();
    return switch (provider) {
      case MockGateway.NAME -> new MockGateway();
      case GeminiGateway.NAME -> new GeminiGateway(props, mapper);
      case OpenAiCompatibleGateway.NAME, OpenAiCompatibleGateway.ALIAS ->
          new OpenAiCompatibleGateway(props, mapper);
      default ->
          throw new IllegalStateException(
              "Unknown slotmind.llm.provider '%s'; use mock, gemini or openai_compatible"
                  .formatted(provider));
    };
  }
}
