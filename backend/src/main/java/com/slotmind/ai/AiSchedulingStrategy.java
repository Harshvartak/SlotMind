package com.slotmind.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.Slot;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.RuleBasedStrategy;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingStrategy;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Asks the model for the best slot, then validates the answer against the context's available
 * slots. Any failure (gateway, bad JSON, a slot that is not one of the open ones) falls back to the
 * rule strategy, so a suggestion is still produced.
 */
@Component("ai")
@Slf4j
public class AiSchedulingStrategy implements SchedulingStrategy {
  private static final Pattern FENCE = Pattern.compile("(?s)^```(?:json)?\\s*(.*?)\\s*```$");

  private final LlmGateway gateway;
  private final RuleBasedStrategy rules;
  private final ObjectMapper mapper;

  public AiSchedulingStrategy(LlmGateway gateway, RuleBasedStrategy rules, ObjectMapper mapper) {
    this.gateway = gateway;
    this.rules = rules;
    this.mapper = mapper;
  }

  @Override
  public List<Recommendation> recommend(SchedulingContext ctx) {
    try {
      return List.of(askModel(ctx));
    } catch (AiException e) {
      log.warn(
          "AI strategy failed for {} ({}); falling back to rules",
          ctx.appointment().getId(),
          e.getMessage());
      return rules.recommend(ctx);
    }
  }

  private Recommendation askModel(SchedulingContext ctx) {
    if (ctx.availableSlots().isEmpty()) {
      throw new AiException("no open slots to choose from");
    }
    String prompt =
        PromptBuilder.build(
            ctx.appointment(), ctx.trigger(), ctx.availableSlots(), ctx.providers());
    return validate(parse(gateway.callLLM(prompt)), ctx);
  }

  private LlmReply parse(String text) {
    Matcher fenced = FENCE.matcher(text.strip());
    String json = fenced.matches() ? fenced.group(1) : text;
    try {
      return mapper.readValue(json, LlmReply.class);
    } catch (JsonProcessingException e) {
      throw new AiException("reply is not valid JSON", e);
    }
  }

  private Recommendation validate(LlmReply reply, SchedulingContext ctx) {
    Slot slot = requireAvailable(reply.slotId(), ctx.availableSlots());
    String owner = slot.getProvider().getId();
    Optional<String> claimed =
        Optional.ofNullable(reply.alternateProviderId()).filter(id -> !id.isBlank());
    if (claimed.isPresent() && !claimed.get().equals(owner)) {
      throw new AiException("alternateProviderId does not own slot " + slot.getId());
    }
    if (reply.confidence() < 0 || reply.confidence() > 1) {
      throw new AiException("confidence out of range: " + reply.confidence());
    }
    if (reply.reasoning() == null || reply.reasoning().isBlank()) {
      throw new AiException("reasoning is empty");
    }
    return new Recommendation(
        slot.getId(),
        sameProvider(ctx.appointment(), owner) ? Optional.empty() : Optional.of(owner),
        reply.confidence(),
        reply.reasoning().strip());
  }

  /** The slot must be one of the open ones we offered: that proves it exists and is OPEN. */
  private Slot requireAvailable(String slotId, List<Slot> available) {
    if (slotId == null || slotId.isBlank()) {
      throw new AiException("slotId is missing");
    }
    return available.stream()
        .filter(s -> s.getId().equals(slotId))
        .findFirst()
        .orElseThrow(() -> new AiException("slotId " + slotId + " is not an available open slot"));
  }

  private static boolean sameProvider(Appointment appt, String providerId) {
    return providerId.equals(appt.getProvider().getId());
  }
}
