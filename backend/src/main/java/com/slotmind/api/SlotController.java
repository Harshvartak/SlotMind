package com.slotmind.api;

import com.slotmind.api.Views.SlotView;
import com.slotmind.domain.SlotStatus;
import com.slotmind.service.CatalogService;
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
  private final CatalogService catalog;

  public record StatusRequest(@NotNull SlotStatus status) {}

  @GetMapping
  public List<SlotView> list() {
    return catalog.slots().stream().map(SlotView::of).toList();
  }

  /** CANCELLED returns immediately; the suggestion for the booked appointment follows async. */
  @PatchMapping("/{id}/status")
  public SlotView setStatus(@PathVariable String id, @Valid @RequestBody StatusRequest r) {
    return SlotView.of(catalog.setSlotStatus(id, r.status()));
  }
}
