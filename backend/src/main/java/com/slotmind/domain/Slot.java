package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {
  @Id private String id;

  @ManyToOne(optional = false)
  private Provider provider;

  private LocalDateTime startTime;
  private int durationMinutes;

  /** Changed only through {@link #moveTo}, so the lifecycle cannot be skipped. */
  @Enumerated(EnumType.STRING)
  @Setter(AccessLevel.NONE)
  private SlotStatus status;

  /**
   * Optimistic lock: a concurrent second accept on the same slot fails at flush. Defaults to 0 in
   * the schema so a plain SQL seed script (like the brief's) can omit the column.
   */
  @Version
  @Column(columnDefinition = "bigint default 0 not null")
  private Long version;

  public void moveTo(SlotStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new InvalidTransitionException("Slot " + id, status, next);
    }
    status = next;
  }
}
