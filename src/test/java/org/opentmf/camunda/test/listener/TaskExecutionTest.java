package org.opentmf.camunda.test.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentmf.camunda.test.execution.TaskExecution;
import org.opentmf.camunda.test.helper.ReceiveTaskManager;
import org.opentmf.camunda.test.helper.TaskExecutionRegistry;
import org.opentmf.camunda.test.model.EventType;
import org.opentmf.camunda.test.util.CamundaExpectationUtil;

@ExtendWith(MockitoExtension.class)
class TaskExecutionTest {

  @Mock private DelegateExecution delegateExecution;

  private CustomTaskExecutionListener receiveTaskListener;

  @BeforeEach
  void setUp() {
    receiveTaskListener = new CustomTaskExecutionListener();
    TaskExecutionRegistry.getInstance().clear();
    ReceiveTaskManager.getInstance().clear();
  }

  @Test
  void test_notifyReceiveTaskListener() {
    String activityId = "activityId";
    String processInstanceId = "processInstanceId";

    ReceiveTaskManager.getInstance().registerReceiveTask(activityId);
    CamundaExpectationUtil.registerMessageCatchExecutionListener()
        .withTaskId(activityId)
        .withCorrelationMessage("mesage")
        .withVariableMap(Map.of())
        .create();

    when(delegateExecution.getCurrentActivityId()).thenReturn(activityId);
    when(delegateExecution.getProcessInstanceId()).thenReturn(processInstanceId);

    receiveTaskListener.notify(delegateExecution);

    var ctx = ReceiveTaskManager.getInstance().getTaskContextMap().get(activityId);
    assertTrue(ctx.getExecutionHelper().getAtomicBoolean().get());

    TaskExecution execution = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);
    assertNull(execution);
  }

  @Test
  void test_notifyReceiveTaskListener_withInvalidTaskId() {
    String activityId = "activityId";

    ReceiveTaskManager.getInstance().registerReceiveTask(activityId);
    CamundaExpectationUtil.registerMessageCatchExecutionListener()
        .withTaskId("invalidTaskId")
        .withCorrelationMessage("mesage")
        .withVariableMap(Map.of())
        .create();

    when(delegateExecution.getCurrentActivityId()).thenReturn(activityId);
    receiveTaskListener.notify(delegateExecution);

    var ctx = ReceiveTaskManager.getInstance().getTaskContextMap().get(activityId);
    assertFalse(ctx.getExecutionHelper().getAtomicBoolean().get());

    TaskExecution execution = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);
    assertNull(execution);
  }

  @Test
  void test_notifyCustomTaskListener() {
    String activityId = "activityId";
    String eventName = "start";

    CamundaExpectationUtil.registerTaskExecutionListener()
        .withEventType(EventType.START)
        .withTaskId(activityId)
        .withVariableMap(Map.of())
        .create();

    when(delegateExecution.getCurrentActivityId()).thenReturn(activityId);
    when(delegateExecution.getEventName()).thenReturn(eventName);

    receiveTaskListener.notify(delegateExecution);

    TaskExecution execution = TaskExecutionRegistry.getInstance().poll(activityId, eventName);
    assertNull(execution);
  }

  @Test
  void test_notifyCustomTaskListener_withExecutionConsumer() {
    String activityId = "consumerTaskId";
    String eventName = "start";
    AtomicReference<String> capturedValue = new AtomicReference<>();

    when(delegateExecution.getVariable("orderId")).thenReturn("ORD-123");

    CamundaExpectationUtil.registerTaskExecutionListener()
        .withEventType(EventType.START)
        .withTaskId(activityId)
        .withExecutionConsumer(
            exec -> capturedValue.set((String) exec.getVariable("orderId")))
        .create();

    when(delegateExecution.getCurrentActivityId()).thenReturn(activityId);
    when(delegateExecution.getEventName()).thenReturn(eventName);

    receiveTaskListener.notify(delegateExecution);

    assertEquals("ORD-123", capturedValue.get());
  }

  @Test
  void test_notifyCustomTaskListener_withExecutionConsumerAndRunnable() {
    String activityId = "bothTaskId";
    String eventName = "start";
    AtomicReference<String> consumerResult = new AtomicReference<>();
    AtomicReference<String> runnableResult = new AtomicReference<>();

    when(delegateExecution.getVariable("status")).thenReturn("active");

    CamundaExpectationUtil.registerTaskExecutionListener()
        .withTaskId(activityId)
        .withExecutionConsumer(
            exec -> consumerResult.set((String) exec.getVariable("status")))
        .withRunnable(() -> runnableResult.set("runnable-executed"))
        .create();

    when(delegateExecution.getCurrentActivityId()).thenReturn(activityId);
    when(delegateExecution.getEventName()).thenReturn(eventName);

    receiveTaskListener.notify(delegateExecution);

    assertEquals("active", consumerResult.get());
    assertEquals("runnable-executed", runnableResult.get());
  }

  @Test
  void test_withCount_registersMultipleTimes() {
    String activityId = "countTaskId";

    CamundaExpectationUtil.registerTaskExecutionListener()
        .withEventType(EventType.START)
        .withTaskId(activityId)
        .withVariableMap(Map.of("key", "value"))
        .withCount(3)
        .create();

    TaskExecution exec1 = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);
    TaskExecution exec2 = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);
    TaskExecution exec3 = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);
    TaskExecution exec4 = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);

    assertNotNull(exec1);
    assertNotNull(exec2);
    assertNotNull(exec3);
    assertNull(exec4);
  }

  @Test
  void test_withCount_throwsOnInvalidCount() {
    var zeroBuilder = CamundaExpectationUtil.registerTaskExecutionListener().withTaskId("test");
    assertThrows(IllegalArgumentException.class, () -> zeroBuilder.withCount(0));

    var negativeBuilder = CamundaExpectationUtil.registerTaskExecutionListener().withTaskId("test");
    assertThrows(IllegalArgumentException.class, () -> negativeBuilder.withCount(-1));
  }

  @Test
  void test_withCount_receiveTasks() {
    String activityId = "countReceiveTaskId";

    ReceiveTaskManager.getInstance().registerReceiveTask(activityId);
    CamundaExpectationUtil.registerMessageCatchExecutionListener()
        .withTaskId(activityId)
        .withCorrelationMessage("msg")
        .withCount(2)
        .create();

    var queue = ReceiveTaskManager.getInstance().getTaskExpectationsMap().get(activityId);
    assertNotNull(queue);
    assertEquals(2, queue.size());
  }

  @Test
  void test_notifyReceiveTaskListener_withExecutionConsumer() {
    String activityId = "consumerReceiveTaskId";
    String processInstanceId = "pid-123";

    ReceiveTaskManager.getInstance().registerReceiveTask(activityId);
    CamundaExpectationUtil.registerMessageCatchExecutionListener()
        .withTaskId(activityId)
        .withCorrelationMessage("msg")
        .withExecutionConsumer(exec -> exec.setVariable("consumed", true))
        .create();

    when(delegateExecution.getCurrentActivityId()).thenReturn(activityId);
    when(delegateExecution.getProcessInstanceId()).thenReturn(processInstanceId);

    receiveTaskListener.notify(delegateExecution);

    var ctx = ReceiveTaskManager.getInstance().getTaskContextMap().get(activityId);
    assertTrue(ctx.getExecutionHelper().getAtomicBoolean().get());
    assertNotNull(ctx.getDelegateExecution());

    var queue = ReceiveTaskManager.getInstance().getTaskExpectationsMap().get(activityId);
    var expectation = queue.peek();
    assertNotNull(expectation.getExecutionConsumer());
  }

  @Test
  void test_registerTaskListener_withoutEventType_defaultsToStart() {
    String activityId = "noEventTypeTaskId";

    CamundaExpectationUtil.registerTaskExecutionListener()
        .withTaskId(activityId)
        .withVariableMap(Map.of("x", "y"))
        .create();

    TaskExecution exec = TaskExecutionRegistry.getInstance().poll(activityId, EventType.START);
    assertNotNull(exec);

    TaskExecution execEnd = TaskExecutionRegistry.getInstance().poll(activityId, EventType.END);
    assertNull(execEnd);
  }

  @Test
  void test_registerMessageCatchExecutionListener_aliasForDeprecated() {
    String activityId = "aliasTaskId";

    ReceiveTaskManager.getInstance().registerReceiveTask(activityId);

    CamundaExpectationUtil.registerMessageCatchExecutionListener()
        .withTaskId(activityId)
        .withCorrelationMessage("msg")
        .create();

    var queue = ReceiveTaskManager.getInstance().getTaskExpectationsMap().get(activityId);
    assertNotNull(queue);
    assertEquals(1, queue.size());
  }
}
