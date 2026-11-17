package com.slotmind.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
  private AppointmentType type;

  @ManyToOne(optional = false)
  private Provider provider;

  @ManyToOne(optional = false)
  private Slot slot;

  @Enumerated(EnumType.STRING)
  private AppointmentStatus status;
}
