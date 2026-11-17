package com.slotmind.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.RuleBasedStrategy;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingStrategy;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Asks the model for the best slot, then validates the answer against the database. Any failure
 * (gateway, bad JSON, invalid or non-OPEN slot) falls back to the rule strategy, so a suggestion is
 * still produced.
 */
@Component("ai")
@Slf4j
public class AiSchedulingStrategy implements SchedulingStrategy {
  private static final Pattern FENCE = Pattern.compile("(?s)^```(?:json)?\\s*(.*?)\\s*```$");

  private final LlmGateway gateway;
  private final RuleBasedStrategy rules;
  private final SlotRepository slots;
  private final ProviderRepository providers;
  private final ObjectMapper mapper;

  public AiSchedulingStrategy(
      LlmGateway gateway,
      RuleBasedStrategy rules,
      SlotRepository slots,
      ProviderRepository providers,
      ObjectMapper mapper) {
    this.gateway = gateway;
    this.rules = rules;
    this.slots = slots;
    this.providers = providers;
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
    Appointment appt = ctx.appointment();
    List<Slot> candidates = rules.openSlots(appt);
    if (candidates.isEmpty()) {
      throw new AiException("no open slots to choose from");
    }
    String prompt = PromptBuilder.build(appt, ctx.trigger(), candidates, providers.findAll());
    return validate(parse(gateway.callLLM(prompt)), appt);
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

  private Recommendation validate(LlmReply reply, Appointment appt) {
    Slot slot = requireUsableSlot(reply.slotId());
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
    boolean sameProvider = owner.equals(appt.getProvider().getId());
    return new Recommendation(
        slot.getId(),
        sameProvider ? Optional.empty() : Optional.of(owner),
        reply.confidence(),
        reply.reasoning().strip());
  }

  private Slot requireUsableSlot(String slotId) {
    if (slotId == null || slotId.isBlank()) {
      throw new AiException("slotId is missing");
    }
    Slot slot =
        slots.findById(slotId).orElseThrow(() -> new AiException("unknown slotId " + slotId));
    if (slot.getStatus() != SlotStatus.OPEN) {
      throw new AiException("slot " + slotId + " is " + slot.getStatus());
    }
    if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
      throw new AiException("slot " + slotId + " is in the past");
    }
    if (slot.getProvider().getStatus() == ProviderStatus.OFFLINE) {
      throw new AiException("slot " + slotId + " belongs to an offline provider");
    }
    return slot;
  }
}
