package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.slotmind.api.ApiExceptionHandler;
import com.slotmind.domain.InvalidTransitionException;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Both a lost race and a forbidden status change must surface as 409, not 500. */
class ApiExceptionHandlerTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void optimisticLockFailureMapsToConflict() {
    var lost = new ObjectOptimisticLockingFailureException(Slot.class, "SLT-005");

    assertEquals(HttpStatus.CONFLICT.value(), handler.conflict(lost).getStatus());
  }

  @Test
  void invalidTransitionMapsToConflictWithTheReason() {
    var forbidden =
        new InvalidTransitionException("Slot SLT-1", SlotStatus.CANCELLED, SlotStatus.BOOKED);

    var problem = handler.invalidTransition(forbidden);

    assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
    assertEquals("Slot SLT-1 cannot go from CANCELLED to BOOKED", problem.getDetail());
  }
}
