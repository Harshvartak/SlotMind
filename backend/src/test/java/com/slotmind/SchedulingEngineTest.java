package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingContextFactory;
import com.slotmind.engine.SchedulingEngine;
import com.slotmind.engine.SchedulingProperties;
import com.slotmind.engine.SchedulingStrategy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Plain unit tests: the registry, the config switch and the strategy contract need no Spring. */
class SchedulingEngineTest {
  private static final Appointment APPOINTMENT = Appointment.builder().id("APT-1").build();
  private static final List<Slot> SLOTS = List.of(Slot.builder().id("SLT-1").build());
  private static final List<Provider> PROVIDERS = List.of(Provider.builder().id("PRV-1").build());
  private static final SchedulingContext CONTEXT =
      new SchedulingContext(APPOINTMENT, TriggerReason.INITIAL, SLOTS, PROVIDERS);

  private static SchedulingStrategy returning(String slotId) {
    return ctx -> List.of(new Recommendation(slotId, Optional.empty(), 1.0, "test"));
  }

  private static SchedulingContextFactory contexts() {
    SchedulingContextFactory contexts = mock(SchedulingContextFactory.class);
    when(contexts.create(any(), any())).thenReturn(CONTEXT);
    return contexts;
  }

  private static SchedulingEngine engineWith(String configured) {
    Map<String, SchedulingStrategy> registry =
        Map.of("rule", returning("FROM-RULE"), "ai", returning("FROM-AI"));
    return new SchedulingEngine(registry, new SchedulingProperties(configured), contexts());
  }

  private static String slotFrom(SchedulingEngine engine) {
    return engine.recommend(APPOINTMENT, TriggerReason.INITIAL).get(0).slotId();
  }

  @Test
  void usesTheConfiguredStrategy() {
    assertEquals("FROM-AI", slotFrom(engineWith("ai")));
    assertEquals("FROM-RULE", slotFrom(engineWith("rule")));
  }

  @Test
  void fallsBackToRuleWhenTheStrategyIsUnknown() {
    assertEquals("FROM-RULE", slotFrom(engineWith("room-constraint")));
  }

  @Test
  void fallsBackToRuleWhenNothingIsConfigured() {
    assertEquals("FROM-RULE", slotFrom(engineWith(null)));
  }

  @Test
  void aStrategyReceivesTheAppointmentTheAvailableSlotsAndTheProviders() {
    AtomicReference<SchedulingContext> seen = new AtomicReference<>();
    SchedulingStrategy spy =
        ctx -> {
          seen.set(ctx);
          return List.of();
        };
    SchedulingEngine engine =
        new SchedulingEngine(Map.of("rule", spy), new SchedulingProperties("rule"), contexts());

    engine.recommend(APPOINTMENT, TriggerReason.INITIAL);

    assertSame(APPOINTMENT, seen.get().appointment());
    assertEquals(SLOTS, seen.get().availableSlots());
    assertEquals(PROVIDERS, seen.get().providers());
  }

  @Test
  void aNewStrategyIsJustAnotherRegisteredImplementation() {
    Map<String, SchedulingStrategy> registry =
        Map.of("rule", returning("FROM-RULE"), "room", returning("FROM-ROOM"));

    SchedulingEngine engine =
        new SchedulingEngine(registry, new SchedulingProperties("room"), contexts());

    assertEquals("FROM-ROOM", slotFrom(engine));
  }
}
