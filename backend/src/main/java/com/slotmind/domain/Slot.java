package com.slotmind.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
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

  @Enumerated(EnumType.STRING)
  private SlotStatus status;

  /** Optimistic lock: a concurrent second accept on the same slot fails at flush. */
  @Version private Long version;
}
