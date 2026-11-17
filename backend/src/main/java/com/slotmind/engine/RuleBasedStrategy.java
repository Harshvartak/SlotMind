package com.slotmind.engine;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Nearest OPEN slot with the same provider, then nearest OPEN slot with a provider of the same
 * specialty.
 */
@Component(RuleBasedStrategy.NAME)
public class RuleBasedStrategy implements SchedulingStrategy {
  public static final String NAME = "rule";
  private static final int MAX_OPTIONS = 3;
  private static final double SAME_PROVIDER_CONFIDENCE = 0.9;
  private static final double ALTERNATE_PROVIDER_CONFIDENCE = 0.75;
  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("EEEE d MMMM 'at' h:mm a");

  private final SlotRepository slots;

  public RuleBasedStrategy(SlotRepository slots) {
    this.slots = slots;
  }

  @Override
  public List<Recommendation> recommend(SchedulingContext ctx) {
    Appointment appt = ctx.appointment();
    Provider current = appt.getProvider();
    List<Slot> open = openSlots(appt);
    Stream<Recommendation> sameProvider =
        open.stream().filter(s -> isProvider(s, current)).map(s -> keep(s, current));
    Stream<Recommendation> sameSpecialty =
        open.stream()
            .filter(s -> !isProvider(s, current))
            .filter(s -> s.getProvider().getSpecialty() == current.getSpecialty())
            .map(s -> switchTo(s, current));
    return Stream.concat(sameProvider, sameSpecialty).limit(MAX_OPTIONS).toList();
  }

  /**
   * Future OPEN slots of working providers, excluding the appointment's own slot, nearest first.
   */
  public List<Slot> openSlots(Appointment appt) {
    return slots
        .findByStatusAndStartTimeAfterOrderByStartTimeAsc(SlotStatus.OPEN, LocalDateTime.now())
        .stream()
        .filter(s -> !s.getId().equals(appt.getSlot().getId()))
        .filter(s -> s.getProvider().getStatus() != ProviderStatus.OFFLINE)
        .toList();
  }

  private static boolean isProvider(Slot slot, Provider provider) {
    return slot.getProvider().getId().equals(provider.getId());
  }

  private static Recommendation keep(Slot slot, Provider current) {
    return new Recommendation(
        slot.getId(),
        Optional.empty(),
        SAME_PROVIDER_CONFIDENCE,
        "We can keep you with %s, who has an opening on %s."
            .formatted(current.getName(), slot.getStartTime().format(WHEN)));
  }

  private static Recommendation switchTo(Slot slot, Provider current) {
    Provider other = slot.getProvider();
    return new Recommendation(
        slot.getId(),
        Optional.of(other.getId()),
        ALTERNATE_PROVIDER_CONFIDENCE,
        "%s is not available, so we found %s, who sees the same kind of patients, on %s."
            .formatted(current.getName(), other.getName(), slot.getStartTime().format(WHEN)));
  }
}
