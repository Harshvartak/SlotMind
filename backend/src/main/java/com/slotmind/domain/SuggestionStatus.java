package com.slotmind.domain;

/** PENDING to ACCEPTED or REJECTED; both are final. */
public enum SuggestionStatus {
  PENDING,
  ACCEPTED,
  REJECTED;

  public boolean canTransitionTo(SuggestionStatus next) {
    return this == next || this == PENDING;
  }
}
