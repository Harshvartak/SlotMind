package com.slotmind.repository;

import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, String> {
  List<Slot> findByStatusAndStartTimeAfterOrderByStartTimeAsc(
      SlotStatus status, LocalDateTime after);
}
