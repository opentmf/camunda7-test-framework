package org.opentmf.camunda.test.chaos;

import java.util.concurrent.atomic.LongAdder;
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests;

/**
 * Counts the external-task traffic that reaches the engine's REST door — including the requests an
 * active {@link EngineOutage} then refuses.
 *
 * <p>That inclusion is the point: the interesting claim during an outage is usually not "the
 * worker got nothing" but "the worker KEPT TRYING". A polling client that survives a dead engine
 * shows up here as a growing {@link #fetchAndLockAttempts()} while the outage is active, which is
 * the in-JVM proxy for what a queue-driven autoscaler would see in production.
 *
 * <p>{@link #queueDepth(String)} completes the picture from the engine side: how many external
 * tasks are waiting on a topic right now — the very number a scaler reacts to. {@link
 * #taskCount(String)} counts every task on the topic, locked or not.
 *
 * <p>Counters are JVM-global (the filter cannot reach a test instance) and cleared by {@code
 * BaseBpmIT}'s {@code beforeEach} and by {@code EngineChaosExtension}.
 *
 * <p><strong>No parallel execution.</strong> Because this state is JVM-global, a test that uses
 * it must not run concurrently with any other test: mark its class {@code @Isolated} (JUnit's
 * {@code org.junit.jupiter.api.parallel.Isolated}) when the suite enables JUnit parallel
 * execution.
 *
 * @see EngineOutageFilter
 * @author Yusuf BOZKURT
 */
public final class ExternalTaskProbe {

  private static final LongAdder FETCH_AND_LOCK = new LongAdder();
  private static final LongAdder COMPLETIONS = new LongAdder();
  private static final LongAdder FAILURES = new LongAdder();

  private ExternalTaskProbe() {}

  static void recordFetchAndLock() {
    FETCH_AND_LOCK.increment();
  }

  static void recordCompletion() {
    COMPLETIONS.increment();
  }

  static void recordFailure() {
    FAILURES.increment();
  }

  /** {@code fetchAndLock} attempts observed since the last {@link #reset()} — refused ones included. */
  public static long fetchAndLockAttempts() {
    return FETCH_AND_LOCK.sum();
  }

  /** {@code complete} attempts observed since the last {@link #reset()} — refused ones included. */
  public static long completionAttempts() {
    return COMPLETIONS.sum();
  }

  /** {@code failure}/{@code bpmnError} reports observed since the last {@link #reset()}. */
  public static long failureReports() {
    return FAILURES.sum();
  }

  /**
   * How many external tasks are waiting on the given topic right now, straight from the engine —
   * the queue a production autoscaler would be watching. A task a worker holds locked is not
   * waiting and is not counted; a task whose lock has expired is waiting again and is counted.
   *
   * @see #taskCount(String)
   */
  public static long queueDepth(String topicName) {
    return BpmnAwareTests.externalTaskService()
        .createExternalTaskQuery()
        .topicName(topicName)
        .notLocked()
        .count();
  }

  /**
   * How many external tasks exist on the given topic right now, locked or not, straight from the
   * engine.
   *
   * @see #queueDepth(String)
   */
  public static long taskCount(String topicName) {
    return BpmnAwareTests.externalTaskService()
        .createExternalTaskQuery()
        .topicName(topicName)
        .count();
  }

  /** Clears all counters. Test-hygiene hook; called by the framework between tests. */
  public static void reset() {
    FETCH_AND_LOCK.reset();
    COMPLETIONS.reset();
    FAILURES.reset();
  }
}
