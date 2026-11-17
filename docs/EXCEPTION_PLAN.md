# Plan: custom business exceptions (implement when required)

Status: deferred. Today the code throws `ResponseStatusException` through the `Errors` helper
(`service/Errors.java`). Adopt this plan when we need stable machine-readable error codes (for
example the UI branching on "slot taken" vs "appointment not reschedulable"), or when a second
client appears.

## Target (per CLAUDE.md)

- One base class carrying an error code and HTTP status.
- One `@RestControllerAdvice` returning `ProblemDetail` with the code as a property.

## Steps

1. Add `exception/ErrorCode` enum: `NOT_FOUND`, `SLOT_UNAVAILABLE`, `PROVIDER_OFFLINE`,
   `INVALID_STATE`, `INVALID_REQUEST`, `CONCURRENT_UPDATE`.
2. Add `exception/SlotMindException extends RuntimeException` with `ErrorCode code` and an
   `HttpStatus status()` derived from the code.
3. Subclasses, only where callers need to catch them: `NotFoundException`, `ConflictException`.
   Skip a class per code.
4. Replace the `Errors` helper bodies to construct these; call sites (`Errors.notFound(...)`,
   `Errors.conflict(...)`) stay unchanged, so the diff is one file.
5. Extend `api/ApiExceptionHandler` to map `SlotMindException` to `ProblemDetail`
   (`setProperty("code", ...)`) and map `ObjectOptimisticLockingFailureException` to
   `CONCURRENT_UPDATE`. Keep one advice class.
6. Convert the remaining direct `ResponseStatusException` in `AppointmentController.suggest`
   (422 "no alternative slot") to `ErrorCode.NO_ALTERNATIVE_SLOT`.
7. Tests: a `@WebMvcTest` asserting status and `code` for not-found, conflict and the 409 race.
8. Frontend: show the `detail` text; branch on `code` only where behavior differs.

## Not needed now

Per-error subclasses, i18n of messages, error-code documentation site.
