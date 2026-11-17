package com.slotmind.repository;

import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
  List<RescheduleSuggestion> findByStatus(SuggestionStatus status);

  Optional<RescheduleSuggestion> findFirstByAppointmentIdAndTriggerReasonAndStatus(
      String appointmentId, TriggerReason reason, SuggestionStatus status);
}
