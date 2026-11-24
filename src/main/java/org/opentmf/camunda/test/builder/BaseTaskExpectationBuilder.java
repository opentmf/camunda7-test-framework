package org.opentmf.camunda.test.builder;



import java.util.Map;
import java.util.function.Consumer;
import org.cibseven.bpm.engine.delegate.DelegateExecution;

/**
 * The `BaseTaskExpectationBuilder` interface serves as a flexible and extensible foundation for
 * creating task expectations within a workflow or process automation context. By leveraging the
 * **Builder Pattern**, this interface provides a fluent API to configure tasks step-by-step using
 * customizable attributes such as task ID, variables, and business logic (via a `Runnable`). Its
 * purpose is to simplify task creation and improve code readability while allowing for
 * extensibility via concrete implementations.
 *
 * <h2>Features</h2>
 *
 * - Facilitate task configuration through fluent, chainable methods. - Provide an interface for
 * setting a task ID, variable mappings, and custom actions. - Serve as a base for more specialized
 * task builders such as `ReceiveTaskExpectationBuilder`. - Make use of generics to ensure type
 * safety and fluent API consistency in extended builders.
 *
 * <h2>Core Methods</h2>
 *
 * <ul>
 *   <li><b>withTaskId(String taskId):</b> Sets a unique identifier for the task.
 *       <ul>
 *         <li>Parameter: `taskId` - A string representing the task's unique ID.
 *         <li>Return Type: A fluent reference to the builder instance.
 *       </ul>
 *   <li><b>withVariableMap(Map&lt;String, Object&gt; variableMap):</b> Adds a set of key-value
 *       pairs (variables) to the task, typically for task-specific data.
 *       <ul>
 *         <li>Parameter: `variableMap` - A map containing variables (keys and values).
 *         <li>Return Type: A fluent reference to the builder instance.
 *       </ul>
 *   <li><b>withRunnable(Runnable runnable):</b> Configures a custom `Runnable` logic for the task
 *       that will be executed during task execution. Note: Runnable does not have access to workflow
 *       variables.
 *       <ul>
 *         <li>Parameter: `runnable` - A `Runnable` instance representing the custom logic.
 *         <li>Return Type: A fluent reference to the builder instance.
 *       </ul>
 *   <li><b>withExecutionConsumer(Consumer&lt;DelegateExecution&gt; consumer):</b> Configures a custom
 *       consumer that receives the `DelegateExecution` context, allowing access to workflow variables
 *       and the ability to read/modify them.
 *       <ul>
 *         <li>Parameter: `consumer` - A `Consumer&lt;DelegateExecution&gt;` that can access and modify
 *             workflow variables via `execution.getVariable()`, `execution.setVariable()`, etc.
 *         <li>Return Type: A fluent reference to the builder instance.
 *       </ul>
 *   <li><b>withCount(int count):</b> Specifies how many times this listener should be registered.
 *       This is useful for tasks that execute multiple times (e.g., in a loop).
 *       <ul>
 *         <li>Parameter: `count` - The number of times to register the listener (default is 1).
 *         <li>Return Type: A fluent reference to the builder instance.
 *       </ul>
 *   <li><b>create():</b> Finalizes the task configuration and prepares it for execution or
 *       registration. This is the terminal step of the fluent API.
 *       <ul>
 *         <li>Parameters: None.
 *         <li>Return Type: void.
 *         <li>Exceptions: Concrete implementations may define specific exceptions if task creation
 *             fails.
 *       </ul>
 * </ul>
 *
 * <h2>How It Works</h2>
 *
 * The interface defines an abstraction for configuring tasks. Concrete implementors, such as
 * `TaskExpectationBuilder` or `ReceiveTaskExpectationBuilder`, extend this interface to provide
 * specialized methods for additional task-specific configurations. The design leverages the
 * **Builder Pattern** to construct tasks in a modular and readable manner, enabling developers to
 * fluently chain configurations before invoking the `create()` method to finalize the task.
 *
 * <h2>Example Usage</h2>
 *
 * Below is an example of how to use a concrete implementation that extends this interface:
 *
 * <pre>{@code
 * TaskExpectationBuilder builder = new TaskExpectationBuilderImpl()
 *     .withTaskId("task123")
 *     .withVariableMap(Map.of("key1", "value1"))
 *     .withRunnable(() -> System.out.println("Task Executed"));
 * builder.create();
 * }</pre>
 *
 * In this example, a task is configured with a task ID, variables, and custom logic, and then
 * finalized with the `create()` method for execution or registration.
 *
 * <h2>Design Benefits</h2>
 *
 * - **Reusability:** Provides a reusable contract for task configuration across different types of
 * tasks. - **Code Cleanliness:** Encourages method chaining to reduce repetitive boilerplate code.
 * - **Extensibility:** Generic type parameter ({@code T extends BaseTaskExpectationBuilder<T>}) allows
 * for specialized implementations without breaking the fluent API pattern.
 *
 * <h2>Extending the Interface</h2>
 *
 * Developers implementing this interface should focus on handling how tasks are processed from the
 * configurations (e.g., registration with a task execution engine).
 *
 * @see TaskExpectationBuilder
 * @see AbstractTaskExpectationBuilder
 * @see ReceiveTaskExpectationBuilder
 * @see Runnable
 * @see Map
 * @see java.util.HashMap
 * @author Yusuf BOZKURT
 */
public interface BaseTaskExpectationBuilder<T extends BaseTaskExpectationBuilder<T>> {

  T withTaskId(String taskId);

  T withVariableMap(Map<String, Object> variableMap);

  T withRunnable(Runnable runnable);

  /**
   * Configures a consumer that receives the {@link DelegateExecution} context, allowing access to
   * workflow variables. The consumer can read variables using {@code execution.getVariable()} and
   * modify them using {@code execution.setVariable()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withExecutionConsumer(execution -> {
   *     String orderId = (String) execution.getVariable("orderId");
   *     execution.setVariable("status", "processed");
   * })
   * }</pre>
   *
   * @param consumer a consumer that receives the DelegateExecution context
   * @return a fluent reference to the builder instance
   */
  T withExecutionConsumer(Consumer<DelegateExecution> consumer);

  /**
   * Specifies how many times this listener should be registered. This is useful for tasks that
   * execute multiple times (e.g., in a loop). Default is 1.
   *
   * @param count the number of times to register the listener
   * @return a fluent reference to the builder instance
   */
  T withCount(int count);

  void create();
}
