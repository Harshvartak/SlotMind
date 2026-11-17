package com.slotmind.domain;

/** A status change the lifecycle does not allow, e.g. a COMPLETED appointment going back. */
public class InvalidTransitionException extends IllegalStateException {
  public InvalidTransitionException(String entity, Object from, Object to) {
    super("%s cannot go from %s to %s".formatted(entity, from, to));
  }
}
