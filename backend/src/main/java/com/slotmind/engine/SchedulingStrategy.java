package com.slotmind.engine;

import java.util.List;

/**
 * Contract for every scheduling strategy. A new strategy (e.g. RoomConstraintStrategy) is
 * one @Component("name") implementing this; nothing else changes.
 */
public interface SchedulingStrategy {
  /** Ordered best-first; empty when nothing fits. */
  List<Recommendation> recommend(SchedulingContext context);
}
