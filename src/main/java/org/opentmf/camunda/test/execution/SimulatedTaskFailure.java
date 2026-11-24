package org.opentmf.camunda.test.execution;

import org.cibseven.bpm.engine.ProcessEngineException;

/**
 * The exception a {@code withFailure(...)} expectation throws — a named type so a stack trace in
 * a test log says "this failure was scripted" instead of looking like a genuine bug, and so the
 * engine treats it exactly like any other delegate failure (job retry, then incident).
 *
 * @see org.opentmf.camunda.test.builder.TaskExpectationBuilder#withFailure(String)
 * @author Yusuf BOZKURT
 */
public class SimulatedTaskFailure extends ProcessEngineException {

  public SimulatedTaskFailure(String message) {
    super(message);
  }
}
