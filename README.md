# Camunda7 Test Framework

The BPMNs contain logic in decision trees and in scripts, but not only that, they contain mandatory information on which input and/or output variables are expected before and after each task. There can be numerous combinations how a BPMN flow can continue depending on what sort of variables is set within the task implementations. 

Before deploying a BPMN to the Camunda-7 server, it is essential to test the BPMN flows with all possible combinations of expectations, input and output variables so that we can be sure that the flow will work as expected in the production environment.

Writing integration tests for the BPMN flows have always been a challenge. There is no straightforward way to perform tests for the BPMN flows. However, Camunda-7 platform contain a large set of APIs which makes it possible to write integration tests with the help of an embedded Camunda-7 server in test classpath.

Our Camunda-7 Test Framework makes it very easy to write an integration test for Camunda-7 BPM platform through intelligent use of its API. What it does briefly is:

- When the Spring Boot context is being initialized, it parses the BPMN files and silently registers listeners for each task.
- These listeners are just placeholders which does nothing, just lets the flow continue.
- In your test class, you have the ability to register your own listeners for each task.
- These registrations essentially are not real task listeners, but they are just adding lambdas to the already registered but initially empty set of tasks to be executed. They can be used to add some behavior to the flow, like setting variables, doing DB inserts, creating incidents etc.
- A special sort of registration is also available for message-waiting BPMN elements (Receive Task, Message Intermediate Catch Event, Boundary Message Event). You can register such elements with a correlation message name and a variable map to set so that the flow will automatically continue.
- After each test, these registrations will be removed implicitly, so that other tests can start with a clean state.
- A flow is started by supplying an initial set of workflow variables.
- Before a flow is started, you should provide your expectations for each task. An expectation can be a mock-server expectation or providing some initial data in a database table. 
- Useful methods to assert whether the test is successful or failed are provided and should be used in the final stage of the test method.

> **Note:** Starting with version 2.0.0, this library uses [CibSeven](https://github.com/cibseven/cibseven) (a community fork of Camunda 7) as its embedded engine. CibSeven is API-compatible with Camunda 7.24.0 and shares the same database schema and REST API. Since this is a test-scoped library, CibSeven artifacts do not affect the runtime classpath of consuming microservices. Starting with version 2.1.1 it uses CibSeven 3.0.0 (Spring Boot 4, Jackson 3); the consuming service's own CibSeven client must be on 3.0.0 as well, so a service still on a 2.2.0 client stays on 2.1.0.

## Usage

Add this test dependency to your project:

### Maven Dependency
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.opentmf</groupId>
      <artifactId>opentmf-versions</artifactId>
      <type>pom</type>
      <scope>import</scope>
      <version>RELEASE</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

```xml 
<dependency>
  <groupId>org.opentmf.camunda</groupId>
  <artifactId>camunda7-test-framework</artifactId>
  <scope>test</scope>
</dependency> 
``` 

### Use `@EnableBpmnTaskListenerPlugin`

This annotation causes the library to parse the BPMNs when the Spring Boot context is being prepared, enabling all the magic.

### Alternative: Extend `BaseBpmIT`

The library provides an abstract base class named BaseBpmIT, which already adds the `@EnableBpmnTaskListenerPlugin` annotation.

### Example Test

Below is a simple example of how to use the helper in your integration tests:

```java
import java.util.Map;
import java.util.UUID;

import org.opentmf.camunda.test.model.EventType;

import static org.opentmf.camunda.test.util.CamundaExpectationUtil.registerTaskExecutionListener;
import static org.opentmf.camunda.test.util.CamundaExpectationUtil.registerMessageCatchExecutionListener;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class MyBpmFlowIT extends BaseBpmIT {

  private static final String TASK_ID_RECEIVE_TASK = "myReceiveTaskId";
  private static final String TASK_ID_SERVICE_TASK = "myServiceTaskId";
  private static final String MESSAGE_NAME = "myMessageName";
  private static final String PDK_SAMPLE_BPMN_FLOW = "processDefinitionKey";

  @Test
  void testMyBpmnFlow_withDefaultStartVariables_finishesSuccessfully() {
    // Prepare mock server expectations for your flow
    setupYourMockServerExpectations();
    String entityId = UUID.randomUUID().toString();

    // Register task variables and correlation message for message-waiting elements
    // Supports: Receive Task, Message Intermediate Catch Event, Boundary Message Event
    registerMessageCatchExecutionListener()
            .withTaskId(TASK_ID_RECEIVE_TASK)
            .withVariableMap(Map.of("status", "success"))
            .withCorrelationMessage(MESSAGE_NAME)
            .create();

    // Use withCount() when a task executes multiple times (e.g., in a loop)
    registerMessageCatchExecutionListener()
            .withTaskId("loopedReceiveTask")
            .withCorrelationMessage("LOOP_MESSAGE")
            .withCount(3)
            .create();

    // Register a runnable to be executed before the task starts
    registerTaskExecutionListener()
            .withEventType(EventType.START)
            .withTaskId(TASK_ID_SERVICE_TASK)
            .withRunnable(() -> repository.saveAndFlush(getEntity(entityId, "acknowledge")))
            .create();

    // Register an execution consumer to access workflow variables
    registerTaskExecutionListener()
            .withEventType(EventType.START)
            .withTaskId("anotherServiceTask")
            .withExecutionConsumer(execution -> {
                String orderId = (String) execution.getVariable("orderId");
                Integer quantity = (Integer) execution.getVariable("quantity");
                execution.setVariable("processed", true);
            })
            .create();

    // Register variables to be set after the task ends
    registerTaskExecutionListener()
            .withEventType(EventType.END)
            .withTaskId(TASK_ID_SERVICE_TASK)
            .withVariableMap(Map.of("entityId", entityId))
            .create();

    // Use the library method to start the process
    ProcessInstance instance = startProcessInstance(PDK_SAMPLE_BPMN_FLOW,
            Map.of("orderId", "1", "orderItemId", "1"));

    // Assert that the process has ended successfully
    assertProcessEnded(instance);
  }

  @Test
  void testMyBpmnFlow_withFailedWaitTask_createsIncident() {
    // Prepare mock server expectations for your flow
    setupYourMockServerExpectations();

    // Register a message catch listener that sets a "failed" status variable
    registerMessageCatchExecutionListener()
            .withTaskId("myTaskId")
            .withCorrelationMessage("myMessageName")
            .withVariableMap(Map.of("status", "failed"))
            .create();

    // Use the library method to start the process
    ProcessInstance instance = startProcessInstance("myProcessDefinitionKey",
            Map.of("orderId", "1", "orderItemId", "1"));

    // Assert that an incident was created with the expected message
    assertIncidentCreated(instance, "Wait task status is failed");
  }

  private void setupYourMockServerExpectations() {
    // Setup your mockserver expectations
  }

  @Override
  public Map<String, Object> getStartProcessVariables() {
    return Map.of(
            "productOrderID", UUID.randomUUID().toString(),
            "productOrderItemID", UUID.randomUUID().toString());
  }
}
```

### Builder Methods

Both `registerTaskExecutionListener()` and `registerMessageCatchExecutionListener()` return builders with fluent APIs:

#### Common Methods (both builders)

| Method | Description |
|--------|-------------|
| `withTaskId(String taskId)` | Sets the BPMN element ID to register the listener for |
| `withVariableMap(Map<String, Object> vars)` | Variables to set on the execution when triggered |
| `withRunnable(Runnable runnable)` | Custom logic to execute when triggered (no access to workflow variables) |
| `withExecutionConsumer(Consumer<DelegateExecution> consumer)` | Custom logic with access to workflow variables via `execution.getVariable()`, `execution.setVariable()`, etc. |
| `withCount(int count)` | Number of times to register (default: 1, useful for loops) |
| `create()` | Finalizes and registers the listener |

#### `registerTaskExecutionListener()` specific

| Method | Description |
|--------|-------------|
| `withEventType(EventType eventType)` | When to trigger: `EventType.START` or `EventType.END` |
| `withFailure(String message)` | Scripted failure: throws the named `SimulatedTaskFailure` when triggered. Mutually exclusive with `withBpmnError` |
| `withBpmnError(String errorCode)` | Raises a `BpmnError` with the given code — drives the model's error boundary event. Mutually exclusive with `withFailure` |
| `withDelay(Duration delay)` | Holds the activity for the given duration before anything else — the declarative slow task |

Variables and consumers registered on the same expectation are applied BEFORE a scripted
failure/error fires, so a failing task can still leave evidence behind for assertions.

`withFailure` turns into an **incident** only when the scripted failures cover every attempt of
an asynchronous continuation: expectations are one-shot, so under the engine's default retry
cycle a single `withFailure` means "fails once, completes on retry". Give the activity
`asyncBefore` with a retry cycle the failures exhaust (e.g. `R1/PT0S`), or register
`withCount(n)` failures matching the configured retries. On a non-async activity the exception
propagates straight to the caller (`startProcessInstance(...)`) instead of creating an incident —
the same applies to `withDelay`, which then blocks the calling thread.

#### `registerMessageCatchExecutionListener()` specific

| Method | Description |
|--------|-------------|
| `withCorrelationMessage(String msg)` | The message name to correlate for continuing the flow |

**Supported BPMN elements for `registerMessageCatchExecutionListener()`:**
- Receive Task
- Message Intermediate Catch Event  
- Boundary Message Event

### Chaos Toolkit (2.1.0)

Deterministic failure simulation against the embedded engine — everything sits behind the
framework's existing extension points and is a complete pass-through until a test arms it.

#### Engine outage — `EngineOutage`

Makes the engine's REST surface answer `503 Service Unavailable` while the engine itself keeps
running: exactly what an external-task worker sees when the engine pod dies, and instantly
reversible. The 503 carries the engine's JSON error shape
(`{"type":"EngineOutage","message":"engine outage simulated by EngineOutage"}`), so the client
logs a refused request as a 503 with that message, not as a body it could not parse.

```java
ProcessInstance instance;
try (EngineOutage outage = EngineOutage.begin()) {
    // starting bypasses REST (in-JVM RuntimeService) — only the client-facing door is dead
    instance = startProcessInstance("WF_My_Process");

    // the worker must NOT escalate while the engine is away:
    assertNoIncidentRaised(instance, Duration.ofSeconds(5));
}
// the engine is "back" — the worker picks the task up and the process finishes
assertProcessEnded(instance);
```

Selective scopes cut a single leg of the external-task protocol instead of the whole engine:

| Scope | Effect |
|-------|--------|
| `OutageScope.ALL` (default) | Every `/engine-rest` request refused — the engine is "down" |
| `OutageScope.FETCH_AND_LOCK` | Only the poll loop starves; everything else works |
| `OutageScope.COMPLETION` | Fetching works, but `complete`/`failure`/`bpmnError` reports are refused — the retry-ladder scenario |

Only one outage can be active at a time; a nested `begin()` fails fast. Prefer
try-with-resources — `BaseBpmIT` and `EngineChaosExtension` also disarm leftovers between tests.

#### Poll traffic and queue depth — `ExternalTaskProbe`

Counts the external-task traffic reaching the REST door, **including requests an outage then
refuses** — which is the point: "the client KEPT polling through the outage" becomes assertable.

```java
long before = ExternalTaskProbe.fetchAndLockAttempts();
// ... outage window ...
assertTrue(ExternalTaskProbe.fetchAndLockAttempts() > before); // the client never gave up
assertEquals(1, ExternalTaskProbe.queueDepth("myTopic"));      // the autoscaler's number
```

`queueDepth(topic)` counts the tasks that are **waiting**: a task a worker holds locked is not
counted, a task whose lock has expired is. `taskCount(topic)` counts every task on the topic,
locked or not.

#### Clock jumps — `EngineClock`

Timer events, follow-up dates and retry back-offs all read the engine clock. Jump it forward and
a `PT24H` timer is due NOW:

```java
ProcessInstance instance = startProcessInstance("WF_With_A_24h_Timer");
EngineClock.jumpBy(Duration.ofHours(25));
assertProcessEnded(instance);   // milliseconds, not a day
```

After a jump the clock KEEPS ADVANCING, shifted by the accumulated offset — a second timer, a
retry back-off or a lock expiry later in the same test still sees elapsing time. When a test
genuinely needs time to stand still, `EngineClock.freezeAt(instant)` pins the clock explicitly
(and says so honestly): nothing time-driven progresses until the next move or `reset()`.

Forward-only by design (a rewound clock confuses acquired jobs and history ordering). Every
clock move also nudges the job executor — without that, an acquisition thread that computed its
wake-up under the old clock strands in the future and later async jobs silently wait it out.

#### Deterministic lock loss — `LockSteward`

The "somebody else holds my task now" rejection without sleeping past lock durations:

```java
LockedExternalTask taskForA = LockSteward.lockAs("worker-A", "myTopic");
LockSteward.expireLock(taskForA.getId());
LockSteward.stealAs("worker-B", "myTopic");
// worker-A's complete(...) is now rejected by the engine — assert your classification logic
```

#### Negative incident assertion — `BaseBpmIT.assertNoIncidentRaised`

The load-bearing assertion of chaos tests: the incident query must stay empty for the WHOLE
window (Awaitility `during`), not merely at its end.

```java
assertNoIncidentRaised(instance, Duration.ofSeconds(5));
```

#### Hygiene — `EngineChaosExtension`

Tests extending `BaseBpmIT` get outage/probe/clock cleanup from its `beforeEach`. For everything
else:

```java
@ExtendWith(EngineChaosExtension.class)
class MyWorkerResilienceIT { ... }
```

**A note on test contexts:** if two IT classes declare different `@SpringBootTest` attributes,
Spring boots TWO application contexts that share the same Testcontainers database (an identical
`jdbc:tc` URL reuses the container) — and the first context's still-live job executor will race
the second class's jobs. Declare identical attributes so the TestContext cache serves ONE
context; the suite also gets significantly faster.

### Overview of Key Classes

Here is a brief overview of the main classes used in the helper:

- `@EnableBpmnTaskListenerPlugin`: This annotation activates the `BpmnTaskListenerPlugin` during tests.
- `BpmnTaskParseListener`: This class is the main entry point of the helper. It listens to parsing events of
  BPMN processes and adds a listener to each Receive Task, Service Task, ExclusiveGateway etc.
- `TaskExecutionRegistry`: This singleton class provides a method to register the expectation of any task.
- `ReceiveTaskManager`: This class is responsible for managing the expectations of tasks that can receive a correlation message.
- `CamundaExpectationUtil`: This class provides utility methods to register the expectations of tasks.

By using the `TaskExecutionRegistry`, you can easily simulate the behavior of asynchronous service tasks and message tasks in your tests.

### Recommended Application Context
If you are using this library, most probably your application is an external Camunda Client, and most probably you are dependent on some set of BPMNs and your application deploys the BPMNs to Camunda 7 on startup with the help of [camunda7-bpmn-sync-service](https://github.com/opentmf/camunda7-bpmn-sync-service). So, your aim is to test the BPMN flows in certain IT tests, but maybe you want to disable Camunda 7 engine to be up and running in other IT tests.

What can you do?

Let's consider the following two application configurations:

#### application-it.yml
Let this one be the base application configuration for your IT tests. You can specify the following Camunda related settings:

```yaml
# this exclude is necessary when the CibSeven REST auto-configuration is not needed
spring:
  autoconfigure:
    exclude:
    - org.cibseven.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration

camunda:
  bpm:
    enabled: false
    client:
      disable-auto-fetching: true
```

And your IT tests that exclude Camunda 7 Embedded engine should provide at least:

`@ActiveProfiles("it")`

#### application-camunda.yaml

This one should be specified in `@ActiveProfiles` section where you want to run Camunda BPMN IT tests.

```yaml
spring:
  autoconfigure:
    exclude: []

camunda:
  bpm:
    enabled: true
    client:
      baseUrl: ....
      disable-auto-fetching: false
```

And your IT tests that must use this library should provide at least:

`@ActiveProfiles({"it", "camunda"})`

The latter profile settings will override the configuration that was set in the previous profiles.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and release notes.
