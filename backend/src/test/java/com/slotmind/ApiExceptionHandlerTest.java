package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.slotmind.api.ApiExceptionHandler;
import com.slotmind.domain.Slot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** The optimistic-lock loser must surface as 409, not 500. */
class ApiExceptionHandlerTest {
  @Test
  void optimisticLockFailureMapsToConflict() {
    var lost = new ObjectOptimisticLockingFailureException(Slot.class, "SLT-005");

    var problem = new ApiExceptionHandler().conflict(lost);

    assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
  }
}
