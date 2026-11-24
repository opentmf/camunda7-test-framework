package org.opentmf.camunda.test.chaos;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Makes the embedded engine's REST surface unreachable — without touching the engine itself.
 *
 * <p>The engine keeps running inside the test JVM; what "dies" is the {@code /engine-rest} door
 * the external-task client talks through: while an outage is active, {@link EngineOutageFilter}
 * refuses matching requests with {@code 503 Service Unavailable}. That is exactly what a worker
 * sees when the real engine pod is gone, and it is instantly reversible — no container restarts,
 * no web-server lifecycle, no timing races.
 *
 * <p>Designed for try-with-resources so an outage can never leak into the next test:
 *
 * <pre>{@code
 * try (EngineOutage outage = EngineOutage.begin()) {
 *   // the client's polls now fail; assert the worker copes:
 *   assertNoIncidentRaised(instance, Duration.ofSeconds(5));
 * } // the engine is "back" here — the process should now run to its end
 * }</pre>
 *
 * <p>Scoped outages cut a single leg of the external-task protocol instead of the whole engine —
 * see {@link OutageScope}. Only one outage can be active at a time; {@link #begin(OutageScope)}
 * inside an active outage is a test bug and fails fast.
 *
 * <p>State is JVM-global (the filter has no access to the test instance), mirroring the
 * framework's registry singletons. {@code BaseBpmIT} disarms any leftover outage in its {@code
 * beforeEach}, and {@code EngineChaosExtension} does the same for tests that do not extend it.
 *
 * @see EngineOutageFilter
 * @see OutageScope
 * @author Yusuf BOZKURT
 */
public final class EngineOutage implements AutoCloseable {

  /** The outage in force, or {@code null}: the INSTANCE, so that only its owner can end it. */
  private static final AtomicReference<EngineOutage> ACTIVE = new AtomicReference<>();

  private final OutageScope scope;

  private EngineOutage(OutageScope scope) {
    this.scope = scope;
  }

  /** Begins a full outage — every {@code /engine-rest} request is refused until {@link #close()}. */
  public static EngineOutage begin() {
    return begin(OutageScope.ALL);
  }

  /** Begins an outage limited to the given scope. Fails fast if one is already active. */
  public static EngineOutage begin(OutageScope scope) {
    EngineOutage outage = new EngineOutage(scope);
    if (!ACTIVE.compareAndSet(null, outage)) {
      throw new IllegalStateException(
          "An engine outage is already active (scope "
              + activeScope()
              + ") — end it before beginning another; overlapping outages make a test unreadable");
    }
    return outage;
  }

  /** The scope in force, or {@code null} when the engine is reachable. */
  public static OutageScope activeScope() {
    EngineOutage active = ACTIVE.get();
    return active == null ? null : active.scope;
  }

  /** Whether any outage is currently in force. */
  public static boolean isActive() {
    return ACTIVE.get() != null;
  }

  /**
   * Disarms whatever outage is active, if any. Test-hygiene hook for {@code beforeEach}/{@code
   * afterEach}; inside a test prefer try-with-resources over calling this directly.
   */
  public static void reset() {
    ACTIVE.set(null);
  }

  /**
   * Ends this outage — the engine's REST surface answers again. Idempotent, and it ends only THIS
   * outage: once this one has ended (closed, or disarmed by {@link #reset()}), closing it again
   * leaves an outage begun since then in force.
   */
  @Override
  public void close() {
    ACTIVE.compareAndSet(this, null);
  }
}
