package com.slotmind.ai;

/** One call to a language model. Implementations are chosen by {@code slotmind.llm.provider}. */
public interface LlmGateway {
  /** Returns the model's raw text reply; throws {@link AiException} on any failure. */
  String callLLM(String prompt);
}
