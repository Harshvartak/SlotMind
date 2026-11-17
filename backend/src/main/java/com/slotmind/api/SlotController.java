package com.slotmind.api;

import com.slotmind.api.Views.SlotView;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.repository.SlotRepository;
import com.slotmind.service.Errors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class SlotController {
  private final SlotRepository slots;

  public record StatusRequest(@NotNull SlotStatus status) {}

  @GetMapping
  public List<SlotView> list() {
    return slots.findAll().stream().map(SlotView::of).toList();
  }

  @PatchMapping("/{id}/status")
  public SlotView setStatus(@PathVariable String id, @Valid @RequestBody StatusRequest r) {
    Slot s = slots.findById(id).orElseThrow(() -> Errors.notFound("Slot", id));
    s.setStatus(r.status());
    return SlotView.of(slots.save(s)); // agentic loop hooks in here in Phase 4
  }
}
