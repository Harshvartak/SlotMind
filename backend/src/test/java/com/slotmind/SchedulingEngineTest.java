package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingEngine;
import com.slotmind.engine.SchedulingProperties;
import com.slotmind.engine.SchedulingStrategy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit tests: the registry and config switch need no Spring context. */
class SchedulingEngineTest {
  private static final SchedulingContext CONTEXT =
      new SchedulingContext(Appointment.builder().build(), TriggerReason.INITIAL);

  private static SchedulingStrategy returning(String slotId) {
    return ctx -> List.of(new Recommendation(slotId, Optional.empty(), 1.0, "test"));
  }

  private static SchedulingEngine engineWith(String configured) {
    Map<String, SchedulingStrategy> registry =
        Map.of("rule", returning("FROM-RULE"), "ai", returning("FROM-AI"));
    return new SchedulingEngine(registry, new SchedulingProperties(configured));
  }

  @Test
  void usesTheConfiguredStrategy() {
    assertEquals("FROM-AI", engineWith("ai").recommend(CONTEXT).get(0).slotId());
    assertEquals("FROM-RULE", engineWith("rule").recommend(CONTEXT).get(0).slotId());
  }

  @Test
  void fallsBackToRuleWhenTheStrategyIsUnknown() {
    assertEquals("FROM-RULE", engineWith("room-constraint").recommend(CONTEXT).get(0).slotId());
  }

  @Test
  void fallsBackToRuleWhenNothingIsConfigured() {
    assertEquals("FROM-RULE", engineWith(null).recommend(CONTEXT).get(0).slotId());
  }
}
