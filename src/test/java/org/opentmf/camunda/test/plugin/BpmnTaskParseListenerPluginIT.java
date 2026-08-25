package org.opentmf.camunda.test.plugin;

import static org.opentmf.camunda.test.util.CamundaExpectationUtil.registerMessageCatchExecutionListener;
import static org.opentmf.camunda.test.util.CamundaExpectationUtil.registerTaskExecutionListener;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;

import org.opentmf.camunda.test.context.CustomManagement;
import org.opentmf.camunda.test.context.CustomManagementRepository;
import org.opentmf.camunda.test.integration.BaseBpmIT;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cibseven.bpm.engine.ProcessEngineException;
import org.cibseven.bpm.engine.repository.ProcessDefinition;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for the {@link BpmnTaskListenerPlugin ReceiveTaskParseListenerPlugin} class.
 * This class tests the behavior of the plugin when it is enabled and the BPMN process is started.
 * The tests simulate the process start, the registration of receive tasks, and the completion of
 * the process. The BPMN process used in these tests is defined in the WF_Sample_WaitInvocation.bpmn
 * file. It contains two receive tasks and sends a message at the end of each task.
 *
 * @author Yusuf Bozkurt
 */
@Sql(scripts = "classpath:db/create-table.sql", executionPhase = BEFORE_TEST_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = "desired.port=8999")
class BpmnTaskParseListenerPluginIT extends BaseBpmIT {

  private static final String PDK_WF_SAMPLE_WAIT_INVOCATION = "WF_Sample_WaitInvocation";
  private static final String TASK_ID_WAIT_STATE_BEFORE = "waitStateBefore";
  private static final String TASK_ID_WAIT_STATE_AFTER = "waitStateAfter";
  private static final String TASK_ID_SERVICE_TASK_EXPECTATION = "service_task_expectation";
  private static final String MESSAGE_RECEIVE_TASK = "job-sub-process-completed";

  @Autowired private CustomManagementRepository repository;

  @Test
  void testBpmnFileDeployment_withValidBpmnDefinitions_deploySuccessfully() {
    ProcessDefinition sampleWaitInvocation = validateDeployment(PDK_WF_SAMPLE_WAIT_INVOCATION);
    Assertions.assertEquals(PDK_WF_SAMPLE_WAIT_INVOCATION, sampleWaitInvocation.getKey());
  }

  /**
   * Tests the start of the BPMN process with valid expectations for the receive tasks. It verifies
   * that the process waits for the next step after the start.
   */
  @Test
  void testBpmnProcessStart_withReceiveTaskValidExpectations_waitingNextStep() {
    // Given

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    // When
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    // Then
    assertProcessWaiting(instance, TASK_ID_WAIT_STATE_AFTER);
    assertNotNull(instance);
  }

  /**
   * Tests the start of the BPMN process with valid expectations for the receive tasks. It verifies
   * that the process is completed after the start.
   */
  @Test
  void testBpmnProcessStart_withReceiveTaskValidExpectations_completedProcess() {
    // Given
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(true))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    // When
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    // Then
    assertProcessEnded(instance);
  }

  @Test
  void testBpmnProcessStart_withoutExpectedOutputVariable_throwsProcessEngineException() {
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeMistakeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();
    Assertions.assertThrows(
        ProcessEngineException.class, () -> startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION));
  }

  @Test
  void testBpmnProcessStart_withUnxpectedOutputVariableValue_createsIncident() {
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(Map.of("status", "unexpected"))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);
    assertIncidentCreated(instance);
  }

  @Test
  void testBpmnProcessStart_withUnexpectedOutputVariableValue_assertIncidentWithMessage() {
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(Map.of("status", "unexpected"))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);
    assertIncidentCreated(instance, "Created incident on purpose");
  }

  @Test
  void testBpmnProcessStart_withRunnable_processStaysWaiting() {
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withRunnable(() -> {})
        .create();

    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);
    assertProcessWaiting(instance, TASK_ID_WAIT_STATE_BEFORE);
  }

  @Test
  void
      testBpmnProcessStart_withReceiveTaskValidExpectationsAndIsFinalStateIsFalse_ProcessWaitingAfterState() {
    // Given
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(false))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    // When
    var instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    // Then
    assertProcessWaiting(instance, TASK_ID_WAIT_STATE_AFTER);
  }

  @Test
  void
      testBpmnProcessStart_withReceiveTaskValidExpectationsAndIsFinalStateIsFalseAfterThatItIsTrue_CompletedProcess() {
    // Given
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(false))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(true))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    // When
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    // Then
    assertProcessEnded(instance);
  }

  @Test
  void testBpmnProcessStart_withServiceTaskListener_CompletedProcess() {
    String entityId = UUID.randomUUID().toString();
    // Given
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap("expectation"))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerTaskExecutionListener()
        .withTaskId(TASK_ID_SERVICE_TASK_EXPECTATION)
        .withVariableMap(Map.of("entityId", entityId))
        .withRunnable(() -> repository.saveAndFlush(getEntity(entityId, "acknowledge")))
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(true))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    // When
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    // Then
    assertProcessEnded(instance);
  }

  @Test
  void testBpmnProcessStart_withExecutionConsumer_completedProcess() {
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .withExecutionConsumer(
            execution -> {
              assertNotNull(execution.getProcessInstanceId());
            })
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(true))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    assertProcessEnded(instance);
  }

  @Test
  void testBpmnProcessStart_withCount_completedProcess() {
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_AFTER)
        .withVariableMap(getWaitStateAfterVariableMap(false))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .withCount(1)
        .create();

    var instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    assertProcessWaiting(instance, TASK_ID_WAIT_STATE_AFTER);
  }

  @Test
  void testBpmnProcessStart_withRegisterInvalidReceiveTaskId_doNothing() {
    // Given
    registerMessageCatchExecutionListener()
        .withTaskId(TASK_ID_WAIT_STATE_BEFORE)
        .withVariableMap(getWaitStateBeforeVariableMap())
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    registerMessageCatchExecutionListener()
        .withTaskId("INVALID_TASK_ID")
        .withVariableMap(getWaitStateBeforeVariableMap("expectation"))
        .withCorrelationMessage(MESSAGE_RECEIVE_TASK)
        .create();

    // When
    ProcessInstance instance = startProcessInstance(PDK_WF_SAMPLE_WAIT_INVOCATION);

    assertProcessWaiting(instance, TASK_ID_WAIT_STATE_AFTER);
  }

  private CustomManagement getEntity(String id, String status) {
    var entity = new CustomManagement();
    entity.setId(id);
    entity.setStatus(status);
    return entity;
  }

  private Map<String, Object> getWaitStateBeforeVariableMap(String status) {
    return Map.of("status", status);
  }

  private Map<String, Object> getWaitStateBeforeVariableMap() {
    return getWaitStateBeforeVariableMap("success");
  }

  private Map<String, Object> getWaitStateAfterVariableMap(boolean isFinalState) {
    return Map.of("isFinalState", isFinalState);
  }

  private Map<String, Object> getWaitStateBeforeMistakeVariableMap() {
    return Map.of("mistake", "success");
  }

  /**
   * Returns the variables that are used to start the process instance. In this case, an empty map
   * is returned.
   *
   * @return A map of variables used to start the process instance.
   */
  @Override
  public Map<String, Object> getStartProcessVariables() {
    return new HashMap<>();
  }
}
