package com.slotmind.api;

import com.slotmind.api.Views.ProviderView;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.repository.ProviderRepository;
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
@RequestMapping("/providers")
@RequiredArgsConstructor
public class ProviderController {
  private final ProviderRepository providers;

  public record StatusRequest(@NotNull ProviderStatus status) {}

  @GetMapping
  public List<ProviderView> list() {
    return providers.findAll().stream().map(ProviderView::of).toList();
  }

  @PatchMapping("/{id}/status")
  public ProviderView setStatus(@PathVariable String id, @Valid @RequestBody StatusRequest r) {
    Provider p = providers.findById(id).orElseThrow(() -> Errors.notFound("Provider", id));
    p.setStatus(r.status());
    return ProviderView.of(providers.save(p)); // agentic loop hooks in here in Phase 4
  }
}
