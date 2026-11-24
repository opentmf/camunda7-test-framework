package org.opentmf.camunda.test.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineOutageTest {

  @AfterEach
  void disarm() {
    EngineOutage.reset();
  }

  @Test
  void begin_defaultScope_isAll() {
    try (EngineOutage outage = EngineOutage.begin()) {
      assertTrue(EngineOutage.isActive());
      assertEquals(OutageScope.ALL, EngineOutage.activeScope());
    }
    assertFalse(EngineOutage.isActive());
    assertNull(EngineOutage.activeScope());
  }

  @Test
  void begin_whileActive_failsFast() {
    try (EngineOutage outage = EngineOutage.begin(OutageScope.FETCH_AND_LOCK)) {
      IllegalStateException refused =
          assertThrows(IllegalStateException.class, () -> EngineOutage.begin(OutageScope.ALL));
      assertTrue(refused.getMessage().contains("already active"));
    }
  }

  @Test
  void close_isIdempotent() {
    EngineOutage outage = EngineOutage.begin();
    outage.close();
    outage.close();
    assertFalse(EngineOutage.isActive());
  }

  @Test
  void close_ofAnOutageEndedByReset_leavesTheNextOutageInForce() {
    EngineOutage first = EngineOutage.begin(OutageScope.COMPLETION);
    EngineOutage.reset();
    try (EngineOutage second = EngineOutage.begin(OutageScope.FETCH_AND_LOCK)) {
      first.close();
      assertTrue(EngineOutage.isActive());
      assertEquals(OutageScope.FETCH_AND_LOCK, EngineOutage.activeScope());
    }
    assertFalse(EngineOutage.isActive());
  }

  @Test
  void secondClose_ofAnEndedOutage_leavesTheNextOutageInForce() {
    EngineOutage first = EngineOutage.begin();
    first.close();
    try (EngineOutage second = EngineOutage.begin(OutageScope.COMPLETION)) {
      first.close();
      assertEquals(OutageScope.COMPLETION, EngineOutage.activeScope());
    }
    assertFalse(EngineOutage.isActive());
  }

  @Test
  void reset_disarmsWhateverIsActive() {
    EngineOutage.begin(OutageScope.COMPLETION);
    EngineOutage.reset();
    assertFalse(EngineOutage.isActive());
  }
}
