package com.slotmind.engine;

import java.util.Optional;

/** One re-book option. Same shape as the LLM JSON contract; empty provider means "keep". */
public record Recommendation(
    String slotId, Optional<String> alternateProviderId, double confidence, String reasoning) {}
