package com.slotmind.events;

import com.slotmind.domain.TriggerReason;

/**
 * Something happened that may leave patients without a valid slot. One event type for both cases;
 * the trigger says how to read {@code subjectId}: a provider id for PROVIDER_OFFLINE, a slot id for
 * SLOT_CANCELLED.
 */
public record RescheduleRequested(TriggerReason trigger, String subjectId) {}
