package com.slotmind.api;

import com.slotmind.api.Views.AppointmentView;
import com.slotmind.api.Views.SuggestionView;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.TriggerReason;
import com.slotmind.service.AppointmentService;
import com.slotmind.service.SuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {
  private final AppointmentService appointments;
  private final SuggestionService suggestions;

  public record BookRequest(
      @NotBlank String patientName,
      @NotNull AppointmentType type,
      @NotBlank String providerId,
      @NotBlank String slotId) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AppointmentView book(@Valid @RequestBody BookRequest r) {
    return AppointmentView.of(
        appointments.book(r.patientName(), r.type(), r.providerId(), r.slotId()));
  }

  @GetMapping
  public List<AppointmentView> list(@RequestParam(required = false) AppointmentStatus status) {
    return appointments.list(status).stream().map(AppointmentView::of).toList();
  }

  /** On-demand reschedule suggestion for one appointment. */
  @PostMapping("/{id}/suggest")
  @ResponseStatus(HttpStatus.CREATED)
  public SuggestionView suggest(@PathVariable String id) {
    return suggestions
        .suggest(id, TriggerReason.INITIAL)
        .map(SuggestionView::of)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "No alternative slot available"));
  }
}
