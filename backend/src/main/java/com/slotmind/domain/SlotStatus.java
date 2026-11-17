package com.slotmind.domain;

/**
 * OPEN to BOOKED (someone books it), BOOKED back to OPEN (its appointment moved away), either to
 * CANCELLED, and CANCELLED back to OPEN (reopened). Repeating the current status is allowed.
 */
public enum SlotStatus {
  OPEN,
  BOOKED,
  CANCELLED;

  public boolean canTransitionTo(SlotStatus next) {
    return this == next
        || switch (this) {
          case OPEN -> next == BOOKED || next == CANCELLED;
          case BOOKED -> next == OPEN || next == CANCELLED;
          case CANCELLED -> next == OPEN;
        };
  }
}
