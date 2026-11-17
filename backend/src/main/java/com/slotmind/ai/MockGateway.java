package com.slotmind.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in for a model so the demo never needs a network or a key. It "chooses" the first
 * open slot listed in the prompt, which is the nearest one.
 */
public class MockGateway implements LlmGateway {
  public static final String NAME = "mock";
  private static final double CONFIDENCE = 0.88;
  private static final Pattern FIRST_SLOT = Pattern.compile("slotId=(\\S+) providerId=(\\S+)");
  private static final String REPLY =
      """
      {"slotId":"%s","alternateProviderId":"%s","confidence":%s,\
      "reasoning":"We found the earliest opening that works for you."}""";

  @Override
  public String callLLM(String prompt) {
    Matcher slot = FIRST_SLOT.matcher(prompt);
    if (!slot.find()) {
      throw new AiException("Mock gateway: the prompt lists no open slot");
    }
    return REPLY.formatted(slot.group(1), slot.group(2), CONFIDENCE);
  }
}
