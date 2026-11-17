package com.slotmind.api;

import com.slotmind.api.Views.ProviderView;
import com.slotmind.domain.ProviderStatus;
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
@RequestMapping("/providers")
@RequiredArgsConstructor
public class ProviderController {
  private final CatalogService catalog;

  public record StatusRequest(@NotNull ProviderStatus status) {}

  @GetMapping
  public List<ProviderView> list() {
    return catalog.providers().stream().map(ProviderView::of).toList();
  }

  /**
   * OFFLINE returns immediately; suggestions for the affected appointments follow asynchronously.
   */
  @PatchMapping("/{id}/status")
  public ProviderView setStatus(@PathVariable String id, @Valid @RequestBody StatusRequest r) {
    return ProviderView.of(catalog.setProviderStatus(id, r.status()));
  }
}
