package org.opentmf.camunda.test.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EngineOutageFilterTest {

  private final EngineOutageFilter filter = new EngineOutageFilter();

  /** Chaos state is engine-global: clear it on both sides so nothing leaks in or out. */
  @BeforeEach
  @AfterEach
  void cleanState() {
    EngineOutage.reset();
    ExternalTaskProbe.reset();
  }

  @Test
  void disarmed_countsButNeverAborts() {
    ContainerRequestContext fetch = request("POST", "external-task/fetchAndLock");

    filter.filter(fetch);

    verify(fetch, never()).abortWith(any());
    assertEquals(1, ExternalTaskProbe.fetchAndLockAttempts());
  }

  @Test
  void fullOutage_abortsEverythingWith503() {
    ContainerRequestContext anyCall = request("GET", "process-definition/count");
    try (EngineOutage outage = EngineOutage.begin()) {
      filter.filter(anyCall);
    }

    Response aborted = capturedAbort(anyCall);
    assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), aborted.getStatus());
  }

  @Test
  void fetchAndLockScope_cutsOnlyThePollLoop() {
    ContainerRequestContext fetch = request("POST", "external-task/fetchAndLock");
    ContainerRequestContext complete = request("POST", "external-task/42/complete");

    try (EngineOutage outage = EngineOutage.begin(OutageScope.FETCH_AND_LOCK)) {
      filter.filter(fetch);
      filter.filter(complete);
    }

    verify(fetch).abortWith(any());
    verify(complete, never()).abortWith(any());
    assertEquals(1, ExternalTaskProbe.fetchAndLockAttempts());
    assertEquals(1, ExternalTaskProbe.completionAttempts());
  }

  @Test
  void completionScope_refusesAllThreeOutcomeReports_butNotFetching() {
    ContainerRequestContext fetch = request("POST", "external-task/fetchAndLock");
    ContainerRequestContext complete = request("POST", "external-task/42/complete");
    ContainerRequestContext failure = request("POST", "external-task/42/failure");
    ContainerRequestContext bpmnError = request("POST", "external-task/42/bpmnError");

    try (EngineOutage outage = EngineOutage.begin(OutageScope.COMPLETION)) {
      filter.filter(fetch);
      filter.filter(complete);
      filter.filter(failure);
      filter.filter(bpmnError);
    }

    verify(fetch, never()).abortWith(any());
    verify(complete).abortWith(any());
    verify(failure).abortWith(any());
    verify(bpmnError).abortWith(any());
    assertEquals(2, ExternalTaskProbe.failureReports());
  }

  @Test
  void refusedAttemptsAreStillCounted() {
    ContainerRequestContext fetch = request("POST", "external-task/fetchAndLock");
    try (EngineOutage outage = EngineOutage.begin()) {
      filter.filter(fetch);
      filter.filter(fetch);
      filter.filter(fetch);
    }
    // The whole point of the probe: a client that keeps polling through an outage is visible.
    assertEquals(3, ExternalTaskProbe.fetchAndLockAttempts());
  }

  private static ContainerRequestContext request(String method, String path) {
    ContainerRequestContext context = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    when(context.getMethod()).thenReturn(method);
    when(context.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn(path);
    return context;
  }

  private static Response capturedAbort(ContainerRequestContext context) {
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(context).abortWith(captor.capture());
    return captor.getValue();
  }
}
