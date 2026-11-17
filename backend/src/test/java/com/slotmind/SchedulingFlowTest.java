package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.engine.Recommendation;
import com.slotmind.engine.SchedulingContext;
import com.slotmind.engine.SchedulingEngine;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.service.SuggestionService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SchedulingFlowTest {
  private static final String OFFLINE_PROVIDER = "PRV-001";
  private static final String SHARED_OPEN_SLOT = "SLT-005";

  @Autowired SchedulingEngine engine;
  @Autowired SuggestionService suggestions;
  @Autowired AppointmentRepository appointments;
  @Autowired ProviderRepository providers;
  @Autowired SlotRepository slots;

  private void setOffline(String providerId) {
    Provider p = providers.findById(providerId).orElseThrow();
    p.setStatus(ProviderStatus.OFFLINE);
    providers.save(p);
  }

  private RescheduleSuggestion suggestOffline(String appointmentId) {
    return suggestions.suggest(appointmentId, TriggerReason.PROVIDER_OFFLINE).orElseThrow();
  }

  @Test
  void ruleStrategyPrefersSameProviderWhenSlotCancelled() {
    Slot s = slots.findById("SLT-003").orElseThrow();
    s.setStatus(SlotStatus.CANCELLED);
    slots.save(s);
    Appointment apt = appointments.findById("APT-003").orElseThrow();

    List<Recommendation> r =
        engine.recommend(new SchedulingContext(apt, TriggerReason.SLOT_CANCELLED));

    assertEquals("SLT-004", r.get(0).slotId());
    assertEquals(Optional.empty(), r.get(0).alternateProviderId());
  }

  @Test
  void ruleStrategyFallsBackToSameSpecialtyWhenProviderOffline() {
    setOffline(OFFLINE_PROVIDER);
    Appointment apt = appointments.findById("APT-001").orElseThrow();

    List<Recommendation> r =
        engine.recommend(new SchedulingContext(apt, TriggerReason.PROVIDER_OFFLINE));

    assertEquals(SHARED_OPEN_SLOT, r.get(0).slotId());
    assertEquals(Optional.of("PRV-004"), r.get(0).alternateProviderId());
  }

  @Test
  void acceptUpdatesAppointmentAndBothSlots() {
    setOffline(OFFLINE_PROVIDER);
    RescheduleSuggestion s = suggestOffline("APT-001");
    assertEquals(
        AppointmentStatus.RESCHEDULE_PENDING,
        appointments.findById("APT-001").orElseThrow().getStatus());

    suggestions.decide(s.getId(), SuggestionStatus.ACCEPTED);

    Appointment apt = appointments.findById("APT-001").orElseThrow();
    assertEquals(AppointmentStatus.RESCHEDULED, apt.getStatus());
    assertEquals(SHARED_OPEN_SLOT, apt.getSlot().getId());
    assertEquals("PRV-004", apt.getProvider().getId());
    assertEquals(SlotStatus.BOOKED, slots.findById(SHARED_OPEN_SLOT).orElseThrow().getStatus());
    assertEquals(SlotStatus.OPEN, slots.findById("SLT-001").orElseThrow().getStatus());
  }

  @Test
  void suggestIsDeduplicatedPerAppointmentAndTrigger() {
    setOffline(OFFLINE_PROVIDER);
    assertEquals(suggestOffline("APT-001").getId(), suggestOffline("APT-001").getId());
  }

  /** Two appointments are offered the same single open slot; both accepted at once. One wins. */
  @Test
  void doubleAcceptOnSameSlotAllowsExactlyOne() throws Exception {
    setOffline(OFFLINE_PROVIDER);
    List<String> ids =
        List.of(suggestOffline("APT-001").getId(), suggestOffline("APT-002").getId());

    ExecutorService pool = Executors.newFixedThreadPool(ids.size());
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    var futures =
        ids.stream()
            .map(id -> pool.submit(() -> acceptWhenReleased(id, start, accepted, rejected)))
            .toList();
    start.countDown();
    for (Future<?> f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertEquals(1, accepted.get());
    assertEquals(1, rejected.get());
    assertEquals(SlotStatus.BOOKED, slots.findById(SHARED_OPEN_SLOT).orElseThrow().getStatus());
  }

  private void acceptWhenReleased(
      String id, CountDownLatch start, AtomicInteger accepted, AtomicInteger rejected) {
    try {
      start.await();
      suggestions.decide(id, SuggestionStatus.ACCEPTED);
      accepted.incrementAndGet();
    } catch (ResponseStatusException | ObjectOptimisticLockingFailureException e) {
      rejected.incrementAndGet();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
