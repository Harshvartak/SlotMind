package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.InvalidTransitionException;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionStatus;
import org.junit.jupiter.api.Test;

/** The lifecycles are rules of the domain model, so they are tested without Spring. */
class StateMachineTest {
  @Test
  void appointmentFollowsTheBriefsLifecycle() {
    Appointment appt = Appointment.builder().id("A").status(AppointmentStatus.CONFIRMED).build();

    appt.moveTo(AppointmentStatus.RESCHEDULE_PENDING);
    appt.moveTo(AppointmentStatus.RESCHEDULED);
    appt.moveTo(AppointmentStatus.COMPLETED);

    assertEquals(AppointmentStatus.COMPLETED, appt.getStatus());
  }

  @Test
  void aRejectedSuggestionSendsAnAppointmentBackToConfirmed() {
    Appointment appt =
        Appointment.builder().id("A").status(AppointmentStatus.RESCHEDULE_PENDING).build();

    appt.moveTo(AppointmentStatus.CONFIRMED);

    assertEquals(AppointmentStatus.CONFIRMED, appt.getStatus());
  }

  @Test
  void aRescheduledAppointmentCanLoseItsSlotAgain() {
    assertTrue(AppointmentStatus.RESCHEDULED.canTransitionTo(AppointmentStatus.RESCHEDULE_PENDING));
  }

  @Test
  void completedIsFinal() {
    for (AppointmentStatus next : AppointmentStatus.values()) {
      if (next != AppointmentStatus.COMPLETED) {
        assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(next), "COMPLETED to " + next);
      }
    }
  }

  @Test
  void anAppointmentCannotSkipStepsOrCompleteWhileAwaitingADecision() {
    assertFalse(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.RESCHEDULED));
    assertFalse(AppointmentStatus.RESCHEDULE_PENDING.canTransitionTo(AppointmentStatus.COMPLETED));
  }

  @Test
  void anIllegalAppointmentMoveThrowsAndLeavesTheStatusAlone() {
    Appointment appt =
        Appointment.builder().id("APT-9").status(AppointmentStatus.COMPLETED).build();

    InvalidTransitionException e =
        assertThrows(
            InvalidTransitionException.class, () -> appt.moveTo(AppointmentStatus.CONFIRMED));

    assertEquals("Appointment APT-9 cannot go from COMPLETED to CONFIRMED", e.getMessage());
    assertEquals(AppointmentStatus.COMPLETED, appt.getStatus());
  }

  @Test
  void repeatingTheCurrentStatusIsAllowed() {
    for (AppointmentStatus s : AppointmentStatus.values()) {
      assertTrue(s.canTransitionTo(s));
    }
    for (SlotStatus s : SlotStatus.values()) {
      assertTrue(s.canTransitionTo(s));
    }
  }

  @Test
  void slotCanBeBookedReleasedCancelledAndReopened() {
    Slot slot = Slot.builder().id("S").status(SlotStatus.OPEN).build();

    slot.moveTo(SlotStatus.BOOKED);
    slot.moveTo(SlotStatus.OPEN);
    slot.moveTo(SlotStatus.CANCELLED);
    slot.moveTo(SlotStatus.OPEN);

    assertEquals(SlotStatus.OPEN, slot.getStatus());
  }

  @Test
  void aCancelledSlotCannotBeBookedWithoutReopeningIt() {
    Slot slot = Slot.builder().id("S").status(SlotStatus.CANCELLED).build();

    assertThrows(InvalidTransitionException.class, () -> slot.moveTo(SlotStatus.BOOKED));
  }

  @Test
  void aSuggestionIsDecidedExactlyOnce() {
    RescheduleSuggestion s =
        RescheduleSuggestion.builder().id("SUG").status(SuggestionStatus.PENDING).build();

    s.moveTo(SuggestionStatus.ACCEPTED);

    assertThrows(InvalidTransitionException.class, () -> s.moveTo(SuggestionStatus.REJECTED));
    assertEquals(SuggestionStatus.ACCEPTED, s.getStatus());
  }
}
