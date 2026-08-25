package org.opentmf.camunda.test.chaos;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentmf.camunda.test.util.CamundaExpectationUtil.registerTaskExecutionListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.cibseven.bpm.engine.ProcessEngineException;
import org.cibseven.bpm.engine.runtime.Job;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests;
import org.junit.jupiter.api.Test;
import org.opentmf.camunda.test.clock.EngineClock;
import org.opentmf.camunda.test.integration.BaseBpmIT;
import org.opentmf.camunda.test.lock.LockSteward;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The chaos toolkit proven against a live embedded engine and a REAL external-task client:
 * outages that starve the client without killing the engine, probes that show the client kept
 * trying, a clock that makes a two-hour timer due in milliseconds, and deterministic lock loss.
 */
// Same @SpringBootTest attributes as BpmnTaskParseListenerPluginIT ON PURPOSE: the Spring
// TestContext cache then serves ONE application context (one engine, one job executor) to both
// classes. A second context on another port shares the same Testcontainers database (identical
// jdbc:tc URL = same container), and its still-live job executor races this class's jobs — the
// async ones lose their one-shot expectations to a competitor engine and the suite goes red.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = "desired.port=8999")
class ChaosToolkitIT extends BaseBpmIT {

  private static final String PDK_EXTERNAL_PROBE = "WF_Chaos_ExternalProbe";
  private static final String PDK_TIMER = "WF_Chaos_Timer";
  private static final String PDK_SCRIPTED = "WF_Chaos_Scripted";
  private static final String PDK_LOCK_ARENA = "WF_Chaos_LockArena";
  private static final String TOPIC_CHAOS_PROBE = "chaosProbe";
  private static final String TOPIC_LOCK_ARENA = "lockArena";
  private static final String TASK_ID_SCRIPTED = "scriptedOutcome";

  /**
   * The N-5 shape, in-process: while the engine is "down" the worker raises no incident and KEEPS
   * polling (the queue a production autoscaler watches stays visible and non-empty); the moment
   * the engine is back, the task is worked and the process runs to its end.
   */
  @Test
  void engineOutage_workerKeepsPollingRaisesNothing_andRecovers() {
    validateDeployment(PDK_EXTERNAL_PROBE);

    ProcessInstance instance;
    try (EngineOutage outage = EngineOutage.begin()) {
      // Starting the process bypasses REST (in-JVM RuntimeService) — the engine itself is fine,
      // only its REST door is dead, exactly like a worker pod outliving an engine pod.
      instance = startProcessInstance(PDK_EXTERNAL_PROBE);

      assertEquals(1, ExternalTaskProbe.queueDepth(TOPIC_CHAOS_PROBE));

      long attemptsAtOutageStart = ExternalTaskProbe.fetchAndLockAttempts();
      await("client keeps polling through the outage")
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(90, TimeUnit.SECONDS)
          .until(() -> ExternalTaskProbe.fetchAndLockAttempts() > attemptsAtOutageStart);

      assertNoIncidentRaised(instance, Duration.ofSeconds(3));
      assertEquals(1, ExternalTaskProbe.queueDepth(TOPIC_CHAOS_PROBE));
    }

    assertProcessEnded(instance);
  }

  /** A two-hour timer becomes due in milliseconds when the engine clock jumps past it. */
  @Test
  void engineClock_jumpMakesTheTimerDue() {
    validateDeployment(PDK_TIMER);
    ProcessInstance instance = startProcessInstance(PDK_TIMER);
    assertProcessWaiting(instance, "twoHourTimer");

    Job timerJob =
        BpmnAwareTests.managementService()
            .createJobQuery()
            .processInstanceId(instance.getId())
            .singleResult();
    assertTrue(
        timerJob.getDuedate().toInstant().isAfter(EngineClock.now().plus(Duration.ofMinutes(115))),
        "the timer must genuinely be ~2h out before the jump");

    EngineClock.jumpBy(Duration.ofHours(3));

    assertTrue(
        timerJob.getDuedate().toInstant().isBefore(EngineClock.now()),
        "after the jump the timer job is due");
    // The job executor picks the now-due job up on its own — no manual nudge, no race with it.
    assertProcessEnded(instance);
  }

  /**
   * {@code withFailure} drives the retry ladder into an incident — declaratively. The activity is
   * asyncBefore with {@code R1/PT0S}, so the job executor's single attempt consumes the scripted
   * failure and exhausts straight into the incident (expectations are one-shot: a manual
   * executeJob here would race the executor for that one shot).
   */
  @Test
  void scriptedFailure_exhaustsIntoAnIncident() {
    validateDeployment(PDK_SCRIPTED);
    registerTaskExecutionListener()
        .withTaskId(TASK_ID_SCRIPTED)
        .withFailure("scripted boom")
        .create();

    ProcessInstance instance = startProcessInstance(PDK_SCRIPTED);

    assertIncidentCreated(instance, "scripted boom");
  }

  /** {@code withBpmnError} takes the model's error boundary — the business-failure path. */
  @Test
  void scriptedBpmnError_takesTheBoundaryPath() {
    validateDeployment(PDK_SCRIPTED);
    registerTaskExecutionListener()
        .withTaskId(TASK_ID_SCRIPTED)
        .withBpmnError("FAILED")
        .create();

    ProcessInstance instance = startProcessInstance(PDK_SCRIPTED);

    assertProcessEnded(instance);
    BpmnAwareTests.assertThat(instance).hasPassed("ScriptedFailEnd");
  }

  /**
   * Deterministic lock loss: the task is unlocked and stolen the moment the test says so — the
   * original worker's completion is rejected by the engine, no sleeping past lock durations.
   */
  @Test
  void lockSteward_lockLossIsDeterministic() {
    validateDeployment(PDK_LOCK_ARENA);
    ProcessInstance instance = startProcessInstance(PDK_LOCK_ARENA);
    assertEquals(1, ExternalTaskProbe.queueDepth(TOPIC_LOCK_ARENA));

    var taskForA = LockSteward.lockAs("worker-A", TOPIC_LOCK_ARENA);
    String contestedTaskId = taskForA.getId();
    LockSteward.expireLock(contestedTaskId);
    var taskForB = LockSteward.stealAs("worker-B", TOPIC_LOCK_ARENA);
    assertEquals(contestedTaskId, taskForB.getId());

    var externalTaskService = BpmnAwareTests.externalTaskService();
    Map<String, Object> noVariables = Map.of();
    assertThrows(
        ProcessEngineException.class,
        () -> externalTaskService.complete(contestedTaskId, "worker-A", noVariables));

    externalTaskService.complete(contestedTaskId, "worker-B", noVariables);
    assertProcessEnded(instance);
  }
}
