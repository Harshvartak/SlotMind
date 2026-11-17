package com.slotmind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SlotRepository;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The brief's Addendum A, loaded exactly as written (with its own NOW() + n times and without a
 * version column), must fit our schema. Our own data.sql is switched off for this test.
 */
@SpringBootTest(
    properties = {"spring.datasource.url=jdbc:h2:mem:${random.uuid}", "spring.sql.init.mode=never"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BriefSeedCompatibilityTest {
  @Autowired DataSource dataSource;
  @Autowired AppointmentRepository appointments;
  @Autowired SlotRepository slots;

  private void loadBriefSeed() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("brief-seed.sql"));
    }
  }

  @Test
  void theBriefsSeedScriptLoadsUnmodifiedAndMapsOntoOurEntities() throws SQLException {
    loadBriefSeed();

    Appointment aisha = appointments.findById("APT-003").orElseThrow();
    assertEquals(4, appointments.count());
    assertEquals("Aisha Patel", aisha.getPatientName());
    assertEquals(AppointmentType.URGENT, aisha.getType());
    assertEquals(AppointmentStatus.CONFIRMED, aisha.getStatus());
    assertEquals("PRV-003", aisha.getProvider().getId());
  }

  @Test
  void slotsFromTheBriefStartWithVersionZeroSoOptimisticLockingWorks() throws SQLException {
    loadBriefSeed();

    Slot slot = slots.findById("SLT-005").orElseThrow();

    assertEquals(0L, slot.getVersion());
    assertEquals(6, slots.count());
  }
}
