package com.slotmind.api;

import com.slotmind.domain.InvalidTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  /** Lost the race for a slot: another accept or booking got there first. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail conflict(ObjectOptimisticLockingFailureException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Slot was just taken by someone else. Refresh and retry.");
  }

  /** A status change the lifecycle forbids (for example reopening a completed appointment). */
  @ExceptionHandler(InvalidTransitionException.class)
  public ProblemDetail invalidTransition(InvalidTransitionException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }
}
