package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reschedule_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleSuggestion {
  @Id private String id;

  @ManyToOne(optional = false)
  private Appointment appointment;

  private String recommendedSlotId;

  /** Column is nullable (same provider kept); read it through {@link #alternateProvider()}. */
  @Getter(AccessLevel.NONE)
  private String alternateProviderId;

  private double confidence;

  @Column(length = 1000)
  private String reasoning;

  /** Changed only through {@link #moveTo}: PENDING to ACCEPTED or REJECTED, then final. */
  @Enumerated(EnumType.STRING)
  @Setter(AccessLevel.NONE)
  private SuggestionStatus status;

  @Enumerated(EnumType.STRING)
  private TriggerReason triggerReason;

  private LocalDateTime createdAt;

  public Optional<String> alternateProvider() {
    return Optional.ofNullable(alternateProviderId);
  }

  public void moveTo(SuggestionStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new InvalidTransitionException("Suggestion " + id, status, next);
    }
    status = next;
  }
}
