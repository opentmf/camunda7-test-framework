package org.opentmf.camunda.test.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.ProcessEngines;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.engine.impl.util.ClockUtil;

/**
 * Moves the engine's clock so time-driven BPMN behavior can be tested without waiting for time.
 *
 * <p>Timer events, follow-up dates, retry back-offs and every other due-date the engine computes
 * all read {@link ClockUtil}. Jumping that clock forward makes a {@code PT24H} timer due NOW —
 * the job executor picks it up on its next acquisition cycle, and a test that used to be
 * "impossible without an overnight run" becomes three lines:
 *
 * <pre>{@code
 * ProcessInstance instance = startProcessInstance("WF_With_A_24h_Timer");
 * EngineClock.jumpBy(Duration.ofHours(25));
 * assertProcessEnded(instance);
 * }</pre>
 *
 * <p>After a {@link #jumpBy(Duration)} the clock KEEPS ADVANCING, shifted by the accumulated
 * offset — a second timer, a retry back-off or a lock expiry later in the same test still sees
 * elapsing time. When a test genuinely needs time to stand still, {@link #freezeAt(Instant)} pins
 * the clock and says so honestly: nothing time-driven progresses until the next move or {@link
 * #reset()}.
 *
 * <p>The clock is engine-global, so hygiene matters more than usual: {@code BaseBpmIT} resets it
 * before each test and {@code EngineChaosExtension} after each — a jumped clock must never leak
 * into a neighbouring test. Jump forward only: the engine tolerates a rewound clock poorly
 * (already-acquired jobs, history ordering), so {@link #jumpBy(Duration)} refuses negatives.
 *
 * @author Yusuf BOZKURT
 */
// java.util.Date is not a choice here: ClockUtil's whole API (setCurrentTime/getCurrentTime/
// offset) speaks Date and offers no java.time overload. The Date usage is confined to that
// boundary — this class's own surface is Instant/Duration throughout.
public final class EngineClock {

  private static long offsetMillis;
  private static boolean frozen;

  private EngineClock() {}

  /**
   * Jumps the engine clock forward by the given amount. The clock keeps advancing afterwards,
   * shifted by the accumulated offset. On a {@link #freezeAt(Instant) frozen} clock the frozen
   * point moves forward instead — time stays stopped. Negative jumps are refused — see class doc.
   */
  public static synchronized void jumpBy(Duration amount) {
    if (amount.isNegative()) {
      throw new IllegalArgumentException(
          "The engine clock only jumps FORWARD (asked for "
              + amount
              + ") — rewinding confuses acquired jobs and history ordering; reset() returns to real time");
    }
    if (frozen) {
      ClockUtil.setCurrentTime(Date.from(now().plus(amount)));
    } else {
      offsetMillis += amount.toMillis();
      ClockUtil.offset(offsetMillis);
    }
    wakeJobExecutor();
  }

  /**
   * FREEZES the engine clock at the given instant: time stands still — no timer fires, no
   * back-off elapses, no lock expires — until the next {@link #jumpBy(Duration)} (which moves the
   * frozen point) or {@link #reset()}. The instant may lie in the past — rewinding confuses the
   * engine (see class doc), so that is the caller's deliberate call. For "skip ahead but keep
   * time flowing", use {@link #jumpBy(Duration)} instead.
   */
  public static synchronized void freezeAt(Instant instant) {
    ClockUtil.setCurrentTime(Date.from(instant));
    offsetMillis = 0;
    frozen = true;
    wakeJobExecutor();
  }

  /** What the engine currently believes "now" is. */
  public static Instant now() {
    return ClockUtil.getCurrentTime().toInstant();
  }

  /** Whether the clock is currently jumped or frozen rather than following real time. */
  public static synchronized boolean isPinned() {
    return frozen || offsetMillis != 0;
  }

  /** Returns the engine to real time. Test-hygiene hook; called by the framework between tests. */
  public static synchronized void reset() {
    ClockUtil.reset();
    offsetMillis = 0;
    frozen = false;
    wakeJobExecutor();
  }

  /**
   * Every clock move ends with a nudge to the job executor. Without it, an acquisition thread
   * that computed its next wake-up under the OLD clock can strand: after a jump-and-reset the
   * timestamp it sleeps toward sits hours in the future of real time, and every async job in the
   * suite silently waits it out — the clock feature would poison the tests that run after it.
   */
  private static void wakeJobExecutor() {
    ProcessEngine engine = ProcessEngines.getDefaultProcessEngine(false);
    if (engine == null) {
      return; // pure unit use — no engine to wake
    }
    if (engine.getProcessEngineConfiguration()
        instanceof ProcessEngineConfigurationImpl configuration) {
      var jobExecutor = configuration.getJobExecutor();
      if (jobExecutor != null && jobExecutor.isActive()) {
        jobExecutor.jobWasAdded();
      }
    }
  }
}
