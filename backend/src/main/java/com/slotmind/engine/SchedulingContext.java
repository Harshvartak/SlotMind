package com.slotmind.engine;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;
import java.util.List;

/**
 * Everything a strategy may use, so strategies are pure functions of this input.
 *
 * @param availableSlots slots the appointment could move to (OPEN, in the future, owned by a
 *     working provider, not its own slot), nearest first
 * @param providers the full provider roster
 */
public record SchedulingContext(
    Appointment appointment,
    TriggerReason trigger,
    List<Slot> availableSlots,
    List<Provider> providers) {}
