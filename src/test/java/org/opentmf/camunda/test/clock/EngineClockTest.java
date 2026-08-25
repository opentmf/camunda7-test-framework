package org.opentmf.camunda.test.clock;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineClockTest {

  private static final Instant FROZEN_TARGET = Instant.parse("2030-01-01T12:00:00Z");
  private static final Duration OBSERVATION_WINDOW = Duration.ofMillis(150);
  private static final Duration PATIENCE = Duration.ofSeconds(5);

  @AfterEach
  void backToRealTime() {
    EngineClock.reset();
  }

  @Test
  void jumpBy_refusesNegativeAmounts() {
    Duration backwards = Duration.ofHours(-1);

    IllegalArgumentException refused =
        assertThrows(IllegalArgumentException.class, () -> EngineClock.jumpBy(backwards));

    assertTrue(refused.getMessage().contains("FORWARD"));
    assertFalse(EngineClock.isPinned());
  }

  @Test
  void jumpBy_shiftsTheClock_andTimeKeepsAdvancing() {
    Instant realBefore = Instant.now();

    EngineClock.jumpBy(Duration.ofHours(2));

    Instant jumped = EngineClock.now();
    assertTrue(
        Duration.between(realBefore.plus(Duration.ofHours(2)), jumped).abs().toSeconds() < 60,
        "the clock must land ~2h ahead of real time, saw " + jumped);
    assertTrue(EngineClock.isPinned());

    // A jumped clock must KEEP ADVANCING — a frozen one would never satisfy this.
    await("jumped clock keeps advancing")
        .pollInterval(20, TimeUnit.MILLISECONDS)
        .atMost(PATIENCE)
        .until(() -> Duration.between(jumped, EngineClock.now()).toMillis() >= 100);
  }

  @Test
  void jumpBy_accumulatesAcrossCalls() {
    Instant realBefore = Instant.now();

    EngineClock.jumpBy(Duration.ofHours(1));
    EngineClock.jumpBy(Duration.ofHours(1));

    assertTrue(
        EngineClock.now().isAfter(realBefore.plus(Duration.ofMinutes(119))),
        "two 1h jumps must accumulate to ~2h ahead");
  }

  @Test
  void freezeAt_stopsTime() {
    EngineClock.freezeAt(FROZEN_TARGET);

    assertEquals(FROZEN_TARGET, EngineClock.now());
    assertTrue(EngineClock.isPinned());

    // Standing still is only provable over a window, not at a single instant.
    await("a frozen clock must not advance")
        .pollInterval(20, TimeUnit.MILLISECONDS)
        .during(OBSERVATION_WINDOW)
        .atMost(PATIENCE)
        .until(() -> FROZEN_TARGET.equals(EngineClock.now()));
  }

  @Test
  void jumpBy_onAFrozenClock_movesTheFrozenPoint_butTimeStaysStopped() {
    EngineClock.freezeAt(FROZEN_TARGET);

    EngineClock.jumpBy(Duration.ofHours(1));

    Instant movedTarget = FROZEN_TARGET.plus(Duration.ofHours(1));
    assertEquals(movedTarget, EngineClock.now());
    await("jumping a frozen clock must not unfreeze it")
        .pollInterval(20, TimeUnit.MILLISECONDS)
        .during(OBSERVATION_WINDOW)
        .atMost(PATIENCE)
        .until(() -> movedTarget.equals(EngineClock.now()));
  }

  @Test
  void reset_returnsToRealTime() {
    EngineClock.jumpBy(Duration.ofDays(1));

    EngineClock.reset();

    assertFalse(EngineClock.isPinned());
    assertTrue(
        Duration.between(Instant.now(), EngineClock.now()).abs().toSeconds() < 5,
        "after reset the engine clock must follow real time again");
  }
}
