package com.slotmind.engine;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.TriggerReason;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds the context for an appointment and hands it to the configured strategy. Used by both the
 * HTTP and the async paths.
 */
@Service
@Slf4j
public class SchedulingEngine {
  private static final String DEFAULT = RuleBasedStrategy.NAME;

  /** Spring injects every SchedulingStrategy bean keyed by its bean name. */
  private final Map<String, SchedulingStrategy> registry;

  private final SchedulingProperties props;
  private final SchedulingContextFactory contexts;

  public SchedulingEngine(
      Map<String, SchedulingStrategy> registry,
      SchedulingProperties props,
      SchedulingContextFactory contexts) {
    this.registry = registry;
    this.props = props;
    this.contexts = contexts;
  }

  public List<Recommendation> recommend(Appointment appointment, TriggerReason trigger) {
    String name = Optional.ofNullable(props.strategy()).orElse(DEFAULT);
    return Optional.ofNullable(registry.get(name))
        .orElseGet(() -> fallback(name))
        .recommend(contexts.create(appointment, trigger));
  }

  private SchedulingStrategy fallback(String name) {
    log.warn(
        "Unknown scheduling strategy '{}', using '{}'. Registered: {}",
        name,
        DEFAULT,
        registry.keySet());
    return registry.get(DEFAULT);
  }
}
