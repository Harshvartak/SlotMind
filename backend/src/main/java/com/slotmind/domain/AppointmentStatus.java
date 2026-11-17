package com.slotmind.domain;

import java.util.List;

/**
 * Lifecycle: CONFIRMED to RESCHEDULE_PENDING to RESCHEDULED to COMPLETED, plus two ways back: a
 * rejected suggestion returns the appointment to CONFIRMED, and an already rescheduled appointment
 * can lose its slot again and go back to RESCHEDULE_PENDING. COMPLETED is final.
 */
public enum AppointmentStatus {
  CONFIRMED,
  RESCHEDULE_PENDING,
  RESCHEDULED,
  COMPLETED;

  /** Appointments that can still be given a suggestion (manually or by the loop). */
  public static final List<AppointmentStatus> ACTIVE =
      List.of(CONFIRMED, RESCHEDULE_PENDING, RESCHEDULED);

  /**
   * What the automatic loop reschedules: bookings the patient has confirmed. That is CONFIRMED as
   * the brief says, plus RESCHEDULED, which is a booking the front desk confirmed with the patient
   * and which must be moved again if its new slot or provider disappears.
   */
  public static final List<AppointmentStatus> CONFIRMED_BOOKING = List.of(CONFIRMED, RESCHEDULED);

  public boolean canTransitionTo(AppointmentStatus next) {
    return this == next
        || switch (this) {
          case CONFIRMED -> next == RESCHEDULE_PENDING || next == COMPLETED;
          case RESCHEDULE_PENDING -> next == RESCHEDULED || next == CONFIRMED;
          case RESCHEDULED -> next == RESCHEDULE_PENDING || next == COMPLETED;
          case COMPLETED -> false;
        };
  }
}
