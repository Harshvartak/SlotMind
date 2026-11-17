package com.slotmind.api;

import com.slotmind.api.Views.SuggestionView;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.service.SuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/suggestions")
@RequiredArgsConstructor
public class SuggestionController {
  private final SuggestionService suggestions;

  public record DecisionRequest(@NotNull SuggestionStatus status) {}

  @GetMapping
  public List<SuggestionView> list(@RequestParam(required = false) SuggestionStatus status) {
    return suggestions.list(status).stream().map(SuggestionView::of).toList();
  }

  /** Front desk accepts or rejects after confirming with the patient. */
  @PatchMapping("/{id}")
  public SuggestionView decide(@PathVariable String id, @Valid @RequestBody DecisionRequest r) {
    return SuggestionView.of(suggestions.decide(id, r.status()));
  }
}
