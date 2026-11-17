package com.slotmind.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The JSON contract the model must return. Fields are checked in AiSchedulingStrategy. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmReply(
    String slotId, String alternateProviderId, double confidence, String reasoning) {}
