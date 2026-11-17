package com.slotmind.service;

import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.events.RescheduleRequested;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Providers and slots. A status change that strands patients publishes a RescheduleRequested event
 * inside the transaction; the agent only reacts after that transaction commits, so it never reads
 * stale state.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {
  private final ProviderRepository providers;
  private final SlotRepository slots;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public List<Provider> providers() {
    return providers.findAll();
  }

  @Transactional(readOnly = true)
  public List<Slot> slots() {
    return slots.findAll();
  }

  @Transactional
  public Provider setProviderStatus(String providerId, ProviderStatus status) {
    Provider provider =
        providers.findById(providerId).orElseThrow(() -> Errors.notFound("Provider", providerId));
    provider.setStatus(status);
    if (status == ProviderStatus.OFFLINE) {
      events.publishEvent(new RescheduleRequested(TriggerReason.PROVIDER_OFFLINE, providerId));
    }
    return providers.save(provider);
  }

  /** The slot lifecycle is enforced by {@link Slot#moveTo}; an illegal move is a 409. */
  @Transactional
  public Slot setSlotStatus(String slotId, SlotStatus status) {
    Slot slot = slots.findById(slotId).orElseThrow(() -> Errors.notFound("Slot", slotId));
    slot.moveTo(status);
    if (status == SlotStatus.CANCELLED) {
      events.publishEvent(new RescheduleRequested(TriggerReason.SLOT_CANCELLED, slotId));
    }
    return slots.save(slot);
  }
}
