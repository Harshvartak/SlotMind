package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.ai.GeminiGateway;
import com.slotmind.ai.LlmConfig;
import com.slotmind.ai.LlmGateway;
import com.slotmind.ai.LlmProperties;
import com.slotmind.ai.MockGateway;
import com.slotmind.ai.OpenAiCompatibleGateway;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmConfigTest {
  private static final int TIMEOUT_MS = 1000;

  private LlmGateway gatewayFor(String provider) {
    LlmProperties props = new LlmProperties(provider, "m", "http://x", "k", TIMEOUT_MS, Map.of());
    return new LlmConfig().llmGateway(props, new ObjectMapper());
  }

  @Test
  void eachProviderNameSelectsItsGateway() {
    assertInstanceOf(MockGateway.class, gatewayFor("mock"));
    assertInstanceOf(GeminiGateway.class, gatewayFor("gemini"));
    assertInstanceOf(OpenAiCompatibleGateway.class, gatewayFor("openai_compatible"));
  }

  @Test
  void theOpenapiCompactSpellingIsAccepted() {
    assertInstanceOf(OpenAiCompatibleGateway.class, gatewayFor("openapi_compact"));
  }

  @Test
  void noProviderMeansMock() {
    assertInstanceOf(MockGateway.class, gatewayFor(""));
  }

  @Test
  void anUnknownProviderFailsFast() {
    assertThrows(IllegalStateException.class, () -> gatewayFor("carrier-pigeon"));
  }
}
