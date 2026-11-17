package com.slotmind.engine;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.TriggerReason;

public record SchedulingContext(Appointment appointment, TriggerReason trigger) {}
