package org.opentmf.camunda.test.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.opentmf.camunda.IncidentLoggerPlugin;
import org.opentmf.camunda.test.chaos.EngineOutage;
import org.opentmf.camunda.test.chaos.ExternalTaskProbe;
import org.opentmf.camunda.test.clock.EngineClock;
import org.opentmf.camunda.test.configuration.EnableBpmnTaskListenerPlugin;
import org.opentmf.camunda.test.helper.ReceiveTaskManager;
import org.opentmf.camunda.test.helper.TaskExecutionRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.awaitility.core.ConditionTimeoutException;
import org.cibseven.bpm.engine.ProcessEngineException;
import org.cibseven.bpm.engine.RepositoryService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.repository.ProcessDefinition;
import org.cibseven.bpm.engine.runtime.Incident;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

/**
 * The `BaseBpmIT` class serves as an abstract base class for integration tests involving BPMN
 * processes, designed to streamline testing for process deployments, process execution, and
 * incident management. It provides utilities to validate deployment, start process instances, and
 * assert process states or incidents, ensuring consistency in test setups and assertions.
 *
 * <p>This class implements the `BpmIT` interface, which standardizes the method for providing start
 * process variables in BPMN integration tests. Additionally, it uses the
 * `@EnableBpmnTaskListenerPlugin` annotation to enable task listener plugins required for effective
 * BPMN testing.
 *
 * <p>The class relies on core Camunda services such as `RepositoryService` and `RuntimeService` to
 * interact with the process definitions and instances.
 *
 * <p>Key utilities include:
 *
 * <p>- **Deployment Validation**: Ensures that a process definition is successfully deployed. -
 * **Process Instance Management**: Provides methods for starting process instances with variables.
 * - **Assertions**: Validates process completion, waiting states, and incident occurrences. -
 * **Incident Querying**: Aggregates incident details for deeper test insights.
 *
 * <p>Usage Scenario: Subclass this base class and write test methods for specific BPMN processes.
 * Utilize utility methods like `startProcessInstance` and `assertProcessEnded` for cleaner, more
 * readable test cases.
 *
 * <p>Example:
 *
 * <p>```java @Test void myProcessTest() { ProcessDefinition definition =
 * validateDeployment("my-process-key"); ProcessInstance instance =
 * startProcessInstance("my-process-key"); assertProcessEnded(instance); } ```
 *
 * <p>Note: The class adopts the Template Method design pattern, where the base class defines the
 * common test utilities while delegating process-specific implementation to subclasses.
 *
 * @see BpmIT
 * @see EnableBpmnTaskListenerPlugin
 * @see RepositoryService
 * @see RuntimeService
 * @see Incident
 * @author Yusuf BOZKURT
 */
@EnableBpmnTaskListenerPlugin
@Import(IncidentLoggerPlugin.class)
public abstract class BaseBpmIT implements BpmIT {

  private RepositoryService repositoryService;
  private RuntimeService runtimeService;

  @BeforeEach
  protected void beforeEach() {
    repositoryService = BpmnAwareTests.repositoryService();
    runtimeService = BpmnAwareTests.runtimeService();
    TaskExecutionRegistry.getInstance().clear();
    ReceiveTaskManager.getInstance().clear();
    // Chaos hygiene: a leaked outage, probe count or pinned clock must never cross tests.
    EngineOutage.reset();
    ExternalTaskProbe.reset();
    EngineClock.reset();
  }

  /**
   * Validates and ensures that a process definition with the given key has been deployed
   * successfully within the BPM Engine. This method fetches the latest version of the specified
   * process definition from the repository, asserting its existence.
   *
   * <p>The primary intent of this method is to verify successful deployment of BPMN process
   * definitions during integration tests. If no process definition matches the key, it will throw
   * an assertion error to highlight the failure.
   *
   * <p><strong>Example Usage:</strong>
   *
   * <pre>{@code
   * @Test
   * void testSuccessfulDeployment() {
   *     ProcessDefinition definition = validateDeployment("sampleProcessKey");
   *     Assertions.assertEquals("sampleProcessKey", definition.getKey());
   * }
   * }</pre>
   *
   * @param processDefinitionKey the unique key identifying the process definition to validate. This
   *     is usually specified in the BPMN diagram of the workflow.
   * @return the {@link ProcessDefinition} object corresponding to the provided key if it exists.
   *     This object contains metadata about the process definition such as its key, ID, version,
   *     and deployment information.
   * @throws AssertionError if the process definition with the given key is not found in the
   *     repository. The error ensures the deployment process integrity during tests.
   */
  protected final ProcessDefinition validateDeployment(String processDefinitionKey) {
    var processDefinition =
        repositoryService
            .createProcessDefinitionQuery()
            .latestVersion()
            .processDefinitionKey(processDefinitionKey)
            .singleResult();
    Assertions.assertNotNull(processDefinition);
    return processDefinition;
  }

  /**
   * Starts a process instance in the BPM Engine based on the given process definition key. This
   * method is used in the context of integration tests to verify the behavior and flow of BPMN
   * processes under server-side conditions. If no custom variables are provided for the process
   * start, an internal default method will supply an empty map.
   *
   * <p>The process instance is immediately kicked off using the `runtimeService`, and then all
   * registered receive task listeners (if any) are triggered via {@code
   * ReceiveTaskManager.getInstance().runAll()}. This ensures proper testing and simulation
   * of scenarios that involve receive tasks.
   *
   * <p><strong>Example Usage:</strong>
   *
   * <pre>{@code
   * ProcessInstance instance = startProcessInstance("myProcessKey");
   * assertNotNull(instance);
   * }</pre>
   *
   * @param processDefinitionKey a unique, case-sensitive string key that identifies the process
   *     within the BPM repository. It should correspond to the key defined in the BPMN model file
   *     of the workflow.
   *     <p>Example: {@code "simpleProcessKey"}
   * @return the {@link ProcessInstance} object representing the started process. This object
   *     provides runtime information about the process, such as its instance ID, execution details,
   *     and active state in the workflow.
   * @throws ProcessEngineException if an error occurs while starting the process, such as an
   *     invalid key, missing variables, or incorrect BPMN configuration.
   * @throws IllegalArgumentException if the given process definition key is null or empty.
   */
  protected final ProcessInstance startProcessInstance(String processDefinitionKey) {
    return startProcessInstance(processDefinitionKey, getStartProcessVariables());
  }

  /**
   * Starts a process instance in the BPM Engine using the specified process definition key and
   * process variables. The method is designed for use in integration tests to validate the behavior
   * of BPMN workflows with custom input variables.
   *
   * <p>This method uses the {@code runtimeService} to trigger a new process instance. Once the
   * process is started, it invokes {@code ReceiveTaskManager.getInstance().runAll()} to
   * handle any receive tasks that may be part of the workflow, ensuring the full simulation of the
   * process execution environment.
   *
   * <p><strong>Example Usage:</strong>
   *
   * <pre>{@code
   * Map<String, Object> variables = new HashMap<>();
   * variables.put("username", "john_doe");
   * variables.put("orderId", 12345);
   * ProcessInstance instance = startProcessInstance("orderApprovalProcess", variables);
   * assertNotNull(instance);
   * assertEquals("orderApprovalProcess", instance.getProcessDefinitionKey());
   * }</pre>
   *
   * @param processDefinitionKey a unique string key identifying the BPMN process to start. This key
   *     must match one defined within the BPM repository. Providing an invalid key will result in a
   *     failure.
   *     <p>Example: {@code "orderApprovalProcess"}
   * @param startProcessVariables a {@link Map} containing key-value pairs of variables to be passed
   *     into the process at startup. These variables may include data required by the workflow
   *     tasks or decision rules.
   *     <p>Example:
   *     <pre>{@code
   * Map<String, Object> variables = new HashMap<>();
   * variables.put("key1", "value1");
   * variables.put("key2", 123);
   *
   * }</pre>
   *     Pass an empty map if no variables are necessary.
   * @return the {@link ProcessInstance} object that represents the newly started process. This
   *     object provides runtime information about the instance, such as the instance ID, definition
   *     key, and execution status.
   * @throws ProcessEngineException if an error occurs during the process start, such as providing a
   *     non-existing process key, invalid variable configurations, or an internal BPM engine
   *     failure.
   * @throws IllegalArgumentException if the process definition key is null or an empty string. This
   *     ensures that the caller provides valid input to avoid runtime failures.
   * @see RuntimeService#startProcessInstanceByKey(String, Map)
   * @see ReceiveTaskManager
   * @see ProcessInstance
   */
  protected final ProcessInstance startProcessInstance(
      String processDefinitionKey, Map<String, Object> startProcessVariables) {
    var processInstance =
        runtimeService.startProcessInstanceByKey(processDefinitionKey, startProcessVariables);
    ReceiveTaskManager.getInstance().runAll();
    return processInstance;
  }

  /**
   * Verifies that a given process instance has fully ended in the BPM engine.
   *
   * <p>This method is designed for use in integration tests to ensure that a running process
   * instance has completed its execution and is no longer present in the runtime. It accomplishes
   * this by periodically querying the BPM engine using the `runtimeService` until the instance is
   * confirmed to have ended or the maximum wait time has elapsed. After ensuring the process has
   * ended, the method clears the {@link TaskExecutionRegistry} to reset task execution states for
   * subsequent tests.
   *
   * <p><strong>Details:</strong> - This method uses a polling mechanism (via the `await` API) to
   * repeatedly check the status of the process instance, ensuring that it does not prematurely
   * assume the process has ended. - The polling interval is 500 milliseconds, and the maximum wait
   * time is set to 60 seconds. - If the process instance does not end within the specified timeout,
   * the test will fail.
   *
   * <p><strong>Usage Example:</strong>
   *
   * <pre>{@code
   * ProcessInstance instance = startProcessInstance("myTestProcessKey");
   * assertProcessEnded(instance);
   * // At this point, the process instance should no longer exist in the runtime.
   * }</pre>
   *
   * @param instance the {@link ProcessInstance} to validate. This should be an active instance that
   *     is expected to complete as part of the test. Make sure the provided instance is not null or
   *     stale, as it directly queries the runtime engine using the instance ID.
   * @throws AssertionError if the process instance is still active after the maximum wait time. The
   *     assertion ensures test integrity by halting execution when the process does not complete as
   *     expected.
   * @see org.awaitility.Awaitility#await(String)
   * @see RuntimeService
   */
  protected final void assertProcessEnded(ProcessInstance instance) {
    await("Await_isEnded")
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(
            () ->
                runtimeService
                        .createProcessInstanceQuery()
                        .processInstanceId(instance.getId())
                        .singleResult()
                    == null);

    TaskExecutionRegistry.getInstance().clear();
  }

  /**
   * Verifies that a given {@link ProcessInstance} is currently waiting at the specified task ID
   * within a BPMN process. This ensures that the process flow has reached a defined wait state
   * (e.g., user task or receive task) as expected during testing.
   *
   * <p>The assertion uses the Awaitility library to continuously query the BPM engine at regular
   * intervals to verify the wait state until a specified maximum time limit is reached. If the
   * condition is not met within the allotted time (60 seconds), the test will fail, ensuring robust
   * validation for process flows in integration tests.
   *
   * <p>Once the expected wait condition is confirmed, a further assertion validates that the
   * process is correctly paused at the provided task ID.
   *
   * <p><strong>Usage Example:</strong>
   *
   * <pre>{@code
   * ProcessInstance instance = startProcessInstance("approvalProcess");
   * assertProcessWaiting(instance, "userTask_approval");
   * // At this point, the process instance should be in a wait state at "userTask_approval".
   * }</pre>
   *
   * @param processInstance the {@link ProcessInstance} representing the active process. This
   *     instance should be obtained during the test where the process is expected to pause (e.g.,
   *     through methods like {@code startProcessInstance}). Ensure this parameter is not null and
   *     corresponds to a valid running process.
   * @param taskId the BPMN task ID (a unique identifier defined in the BPMN model file) where the
   *     process instance is expected to wait. This ID must correspond to a user task, receive task,
   *     or any other wait state in the process definition.
   *     <p>Example: {@code "userTask_approval"}
   * @throws AssertionError if the process instance is not waiting at the provided task ID within
   *     the maximum wait time of 60 seconds. This ensures the process flow adheres to expected
   *     execution behavior under test.
   * @see org.awaitility.Awaitility#await(String)
   * @see ProcessInstance
   */
  protected final void assertProcessWaiting(ProcessInstance processInstance, String taskId) {
    await("Await_isWaiting")
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> isProcessWaiting(processInstance, taskId));

    BpmnAwareTests.assertThat(processInstance).isWaitingAt(taskId);
  }

  /**
   * Asserts that an incident has been created for the specified process instance. This method
   * ensures that an incident with the specified message (or any incident, if the message is null)
   * has been generated for the given process instance within a specified time frame. The method
   * uses polling to wait for the incident to appear and validates its presence.
   *
   * <p>This utility is typically used in end-to-end testing scenarios to verify that the system
   * successfully triggers incidents under specific circumstances.
   *
   * <p><strong>Execution Details:</strong>
   *
   * <ul>
   *   <li>Polls every second to query incidents related to the provided process instance.
   *   <li>Continues polling for a maximum duration of 120 seconds.
   *   <li>Checks if the incident list is not empty to confirm incident creation.
   *   <li>If an `incidentMessage` is provided, ensures that at least one incident's message
   *       contains the specified string.
   * </ul>
   *
   * <p><strong>Usage Example:</strong>
   *
   * <pre>
   * // Example process instance and incident message
   * ProcessInstance instance = startTestProcess();
   * String expectedMessage = "critical error";
   *
   * // Perform the assertion
   * assertIncidentCreated(instance, expectedMessage);
   * </pre>
   *
   * @param processInstance The {@link ProcessInstance} for which incidents are being verified.
   *     Represents the process in the workflow engine. Must not be null.
   * @param incidentMessage The expected message of the incident. If null, the method will only
   *     check for the existence of any incident (ignoring the content of the message). If not null,
   *     the message content is verified to include the value provided.
   * @throws IllegalArgumentException If the `processInstance` is null.
   * @throws AssertionError If no incident is created within the polling duration, or if no incident
   *     contains the expected `incidentMessage` when provided.
   */
  protected final void assertIncidentCreated(
      ProcessInstance processInstance, String incidentMessage) {
    List<Incident> incidents =
        await("assertIncidentCreated(" + incidentMessage + ")")
            // 1s poll (was 10s): an incident that lands in the first second used to still cost a
            // 10-second wait per assertion — across a suite that is minutes of dead time.
            .pollInterval(1, TimeUnit.SECONDS)
            .atMost(120, TimeUnit.SECONDS)
            .until(() -> this.queryIncidents(processInstance), list -> !list.isEmpty());
    assertTrue(
        incidents.stream()
            .anyMatch(
                incident ->
                    incidentMessage == null
                        || (incident.getIncidentMessage() != null
                            && incident.getIncidentMessage().contains(incidentMessage))));
  }

  /**
   * Asserts that an incident has been created for the specified process instance. This method
   * verifies the existence of any incident related to the given process instance within a specified
   * time frame but does not check the incident message.
   *
   * <p>This method is a convenience wrapper for {@link #assertIncidentCreated(ProcessInstance,
   * String)} and calls it with a `null` message, meaning it only checks for the presence of an
   * incident without verifying its content.
   *
   * <p><strong>Execution Details:</strong>
   *
   * <ul>
   *   <li>Polls every second to query incidents related to the provided process instance.
   *   <li>Continues polling for a maximum duration of 120 seconds.
   *   <li>Ensures that at least one incident exists for the process instance.
   * </ul>
   *
   * <p><strong>Usage Example:</strong>
   *
   * <pre>
   * // Example process instance
   * ProcessInstance instance = startTestProcess();
   *
   * // Perform the assertion
   * assertIncidentCreated(instance);
   * </pre>
   *
   * @param processInstance The {@link ProcessInstance} for which incidents are being verified.
   *     Represents the process in the workflow engine. Must not be null.
   * @throws IllegalArgumentException If the `processInstance` is null.
   * @throws AssertionError If no incident is created within the polling duration.
   */
  protected final void assertIncidentCreated(ProcessInstance processInstance) {
    assertIncidentCreated(processInstance, null);
  }

  /**
   * Asserts that NO incident appears for the process instance while the given duration elapses —
   * the negative twin of {@link #assertIncidentCreated(ProcessInstance, String)}.
   *
   * <p>This is the load-bearing assertion of chaos tests: a worker that survives an engine
   * outage, a refused completion or a starved poll loop proves it precisely by NOT escalating —
   * the process must still be incident-free when the chaos ends. Uses Awaitility's {@code
   * during}: the incident query must stay empty for the WHOLE window, not merely at its end.
   *
   * <p><strong>Usage Example:</strong>
   *
   * <pre>{@code
   * try (EngineOutage outage = EngineOutage.begin()) {
   *   assertNoIncidentRaised(instance, Duration.ofSeconds(5));
   * }
   * }</pre>
   *
   * @param processInstance the {@link ProcessInstance} that must stay incident-free. Must not be
   *     null.
   * @param window how long the instance must remain incident-free. Choose it longer than at least
   *     one client poll cycle, or the assertion proves nothing about the worker's behavior.
   * @throws ConditionTimeoutException if any incident appears within the window. Its message
   *     names each incident seen (type, activity, message); the waiting library's own timeout is
   *     its cause.
   */
  protected final void assertNoIncidentRaised(ProcessInstance processInstance, Duration window) {
    AtomicReference<List<Incident>> seen = new AtomicReference<>(List.of());
    try {
      await("assertNoIncidentRaised")
          .pollInterval(250, TimeUnit.MILLISECONDS)
          .during(window)
          .atMost(window.plusSeconds(10))
          .until(
              () -> {
                List<Incident> incidents = queryIncidents(processInstance);
                if (!incidents.isEmpty()) {
                  seen.set(incidents);
                }
                return incidents.isEmpty();
              });
    } catch (ConditionTimeoutException timeout) {
      List<Incident> incidents = seen.get();
      if (incidents.isEmpty()) {
        throw timeout;
      }
      throw new ConditionTimeoutException(
          "Expected no incident for process instance "
              + processInstance.getId()
              + " within "
              + window
              + ", but "
              + incidents.size()
              + " appeared: "
              + String.join("; ", incidents.stream().map(BaseBpmIT::describe).toList()),
          timeout);
    }
  }

  private static String describe(Incident incident) {
    return "[type="
        + incident.getIncidentType()
        + ", activity="
        + incident.getActivityId()
        + ", message="
        + incident.getIncidentMessage()
        + "]";
  }

  private List<Incident> queryIncidents(ProcessInstance instance) {
    List<Incident> incidents =
        runtimeService
            .createIncidentQuery()
            .processInstanceId(instance.getProcessInstanceId())
            .list();

    List<Incident> rootIncidents =
        incidents.stream()
            .filter(
                incident ->
                    StringUtils.hasText(incident.getRootCauseIncidentId())
                        && !incident.getId().equals(incident.getRootCauseIncidentId()))
            .map(
                incident ->
                    runtimeService
                        .createIncidentQuery()
                        .incidentId(incident.getRootCauseIncidentId())
                        .singleResult())
            .toList();

    return Stream.concat(incidents.stream(), rootIncidents.stream()).toList();
  }

  private boolean isProcessWaiting(ProcessInstance processInstance, String taskId) {
    List<ProcessInstance> processInstances =
        BpmnAwareTests.runtimeService().createProcessInstanceQuery().activityIdIn(taskId).list();

    if (!processInstances.isEmpty()) {
      return processInstances.stream()
          .anyMatch(
              instance ->
                  Objects.equals(
                      instance.getProcessInstanceId(), processInstance.getProcessInstanceId()));
    }
    return false;
  }
}
