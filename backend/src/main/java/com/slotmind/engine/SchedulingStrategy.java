package com.slotmind.engine;

import java.util.List;

/**
 * Contract for every scheduling strategy: given an appointment plus the available slots and
 * providers (all in the {@link SchedulingContext}), return ordered recommendations. A new strategy
 * (e.g. RoomConstraintStrategy) is one @Component("name") implementing this; nothing else changes.
 */
public interface SchedulingStrategy {
  /** Ordered best-first; empty when nothing fits. */
  List<Recommendation> recommend(SchedulingContext context);
}
