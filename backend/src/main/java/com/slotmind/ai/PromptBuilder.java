package com.slotmind.ai;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the two prompts: provider offline (a different provider may be needed) and slot cancelled
 * (the same provider with a new slot is usually enough). The patient's name is deliberately not
 * sent to the model; only what the choice needs.
 */
public final class PromptBuilder {
  private static final String OFFLINE_SITUATION =
      """
      The patient's provider is OFFLINE, so this appointment must move. Prefer a provider with \
      the same specialty. Keep the new time as close to the original as possible; if the \
      appointment type is URGENT, choose the earliest suitable slot.""";

  private static final String CANCELLED_SITUATION =
      """
      The patient's slot was CANCELLED, but the provider is still working. Prefer another open \
      slot with the SAME provider. Only suggest a different provider if no slot with the same \
      provider suits the patient.""";

  private static final String TEMPLATE =
      """
      You are a scheduling assistant at a medical clinic front desk.
      %s

      Appointment type: %s
      Original time: %s
      Current provider: %s (%s, %s)

      Open slots (pick exactly one slotId from this list):
      %s

      Provider roster:
      %s

      Reply with ONLY a JSON object, no markdown:
      {"slotId":"<id>","alternateProviderId":"<providerId that owns the chosen slot>",\
      "confidence":<number from 0 to 1>,"reasoning":"<text>"}
      The reasoning is read aloud to the patient by the front desk: one or two short, friendly \
      sentences, no jargon, no ids, no mention of AI or scoring.
      """;

  private PromptBuilder() {}

  public static String build(
      Appointment appt, TriggerReason trigger, List<Slot> candidates, List<Provider> roster) {
    Provider current = appt.getProvider();
    String situation = isProviderOffline(appt, trigger) ? OFFLINE_SITUATION : CANCELLED_SITUATION;
    return TEMPLATE.formatted(
        situation,
        appt.getType(),
        appt.getSlot().getStartTime(),
        current.getName(),
        current.getSpecialty(),
        current.getStatus(),
        lines(candidates, PromptBuilder::slotLine),
        lines(roster, PromptBuilder::providerLine));
  }

  /** A manual (INITIAL) request on an offline provider is the same situation as the event. */
  private static boolean isProviderOffline(Appointment appt, TriggerReason trigger) {
    return trigger == TriggerReason.PROVIDER_OFFLINE
        || appt.getProvider().getStatus() == ProviderStatus.OFFLINE;
  }

  private static String slotLine(Slot s) {
    return "- slotId=%s providerId=%s start=%s durationMinutes=%d"
        .formatted(s.getId(), s.getProvider().getId(), s.getStartTime(), s.getDurationMinutes());
  }

  private static String providerLine(Provider p) {
    return "- providerId=%s name=%s specialty=%s status=%s"
        .formatted(p.getId(), p.getName(), p.getSpecialty(), p.getStatus());
  }

  private static <T> String lines(List<T> items, Function<T, String> format) {
    return items.stream().map(format).collect(Collectors.joining("\n"));
  }
}
