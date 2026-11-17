package com.slotmind.ai;

/** Any failure of the AI path (gateway, parsing, validation). The strategy falls back to rules. */
public class AiException extends RuntimeException {
  public AiException(String message) {
    super(message);
  }

  public AiException(String message, Throwable cause) {
    super(message, cause);
  }
}
