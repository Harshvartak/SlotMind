package com.slotmind.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Picks the configured strategy from the registry. Used by both HTTP and async paths. */
@Service
@Slf4j
public class SchedulingEngine {
  private static final String DEFAULT = RuleBasedStrategy.NAME;

  /** Spring injects every SchedulingStrategy bean keyed by its bean name. */
  private final Map<String, SchedulingStrategy> registry;

  private final SchedulingProperties props;

  public SchedulingEngine(Map<String, SchedulingStrategy> registry, SchedulingProperties props) {
    this.registry = registry;
    this.props = props;
  }

  public List<Recommendation> recommend(SchedulingContext context) {
    String name = Optional.ofNullable(props.strategy()).orElse(DEFAULT);
    return Optional.ofNullable(registry.get(name))
        .orElseGet(() -> fallback(name))
        .recommend(context);
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
