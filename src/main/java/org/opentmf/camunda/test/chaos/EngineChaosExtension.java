package org.opentmf.camunda.test.chaos;

import org.opentmf.camunda.test.clock.EngineClock;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Guarantees chaos state never leaks past a test: after each test method, any active {@link
 * EngineOutage} is disarmed, {@link ExternalTaskProbe} counters are cleared and a pinned {@link
 * EngineClock} returns to real time.
 *
 * <p>Tests extending {@code BaseBpmIT} get the same hygiene from its {@code beforeEach}; this
 * extension is for everything else — register it on any test class that touches the chaos tools
 * directly:
 *
 * <pre>{@code
 * @ExtendWith(EngineChaosExtension.class)
 * class MyWorkerResilienceIT { ... }
 * }</pre>
 *
 * <p>Cleanup between tests does not make concurrent tests safe: while one test's outage or clock
 * jump is active, every other test in the JVM sees it. With JUnit parallel execution enabled,
 * mark each chaos test class {@code @Isolated}.
 *
 * @author Yusuf BOZKURT
 */
public class EngineChaosExtension implements AfterEachCallback {

  @Override
  public void afterEach(ExtensionContext context) {
    EngineOutage.reset();
    ExternalTaskProbe.reset();
    EngineClock.reset();
  }
}
