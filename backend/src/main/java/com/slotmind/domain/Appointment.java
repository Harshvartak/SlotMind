package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {
  @Id private String id;
  private String patientName;

  @Enumerated(EnumType.STRING)
  @Column(name = "appointment_type")
  private AppointmentType type;

  @ManyToOne(optional = false)
  private Provider provider;

  @ManyToOne(optional = false)
  private Slot slot;

  /** Changed only through {@link #moveTo}, so the lifecycle cannot be skipped. */
  @Enumerated(EnumType.STRING)
  @Setter(AccessLevel.NONE)
  private AppointmentStatus status;

  public void moveTo(AppointmentStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new InvalidTransitionException("Appointment " + id, status, next);
    }
    status = next;
  }
}
