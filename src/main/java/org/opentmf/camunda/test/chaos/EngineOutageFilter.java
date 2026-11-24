package org.opentmf.camunda.test.chaos;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * The chaos valve on the engine's REST door.
 *
 * <p>Sits in front of every {@code /engine-rest} resource (registered by {@code JerseyConfig},
 * {@link PreMatching} so even unmatched paths are covered), does two small jobs and nothing else:
 *
 * <ol>
 *   <li>feeds {@link ExternalTaskProbe} — every external-task request is counted, refused or not,
 *       so a test can prove a client KEPT polling through an outage;
 *   <li>enforces {@link EngineOutage} — while one is active, requests inside its {@link
 *       OutageScope} are aborted with {@code 503 Service Unavailable} before any engine code
 *       runs, which is what a worker sees when the engine pod is genuinely gone. The body is the
 *       engine's own JSON error shape ({@link #REFUSAL_BODY}), so the external-task client logs
 *       the refusal instead of failing to parse it.
 * </ol>
 *
 * <p>Disarmed (the default), the filter is a counter and nothing more — zero behavioral change
 * for every existing consumer of the framework.
 *
 * @see EngineOutage
 * @see ExternalTaskProbe
 * @author Yusuf BOZKURT
 */
@Provider
@PreMatching
public class EngineOutageFilter implements ContainerRequestFilter {

  private static final String EXTERNAL_TASK_SEGMENT = "external-task";
  private static final String FETCH_AND_LOCK_SEGMENT = "fetchAndLock";

  /** The 503's body, in the engine's JSON error shape ({@code type}, {@code message}). */
  static final String REFUSAL_BODY =
      "{\"type\":\"EngineOutage\",\"message\":\"engine outage simulated by EngineOutage\"}";

  @Override
  public void filter(ContainerRequestContext requestContext) {
    String path = requestContext.getUriInfo().getPath();
    boolean fetchAndLock = isFetchAndLock(requestContext, path);
    boolean completion = isCompletion(requestContext, path);

    if (fetchAndLock) {
      ExternalTaskProbe.recordFetchAndLock();
    } else if (completion && path.endsWith("/complete")) {
      ExternalTaskProbe.recordCompletion();
    } else if (completion) {
      ExternalTaskProbe.recordFailure();
    }

    OutageScope scope = EngineOutage.activeScope();
    if (scope == null) {
      return;
    }
    boolean refused =
        switch (scope) {
          case ALL -> true;
          case FETCH_AND_LOCK -> fetchAndLock;
          case COMPLETION -> completion;
        };
    if (refused) {
      requestContext.abortWith(
          Response.status(Response.Status.SERVICE_UNAVAILABLE)
              .type(MediaType.APPLICATION_JSON_TYPE)
              .entity(REFUSAL_BODY)
              .build());
    }
  }

  private static boolean isFetchAndLock(ContainerRequestContext requestContext, String path) {
    return HttpMethod.POST.equals(requestContext.getMethod())
        && path.contains(EXTERNAL_TASK_SEGMENT)
        && path.endsWith(FETCH_AND_LOCK_SEGMENT);
  }

  /** Any of the three outcome reports: {@code complete}, {@code failure}, {@code bpmnError}. */
  private static boolean isCompletion(ContainerRequestContext requestContext, String path) {
    return HttpMethod.POST.equals(requestContext.getMethod())
        && path.contains(EXTERNAL_TASK_SEGMENT + "/")
        && (path.endsWith("/complete") || path.endsWith("/failure") || path.endsWith("/bpmnError"));
  }
}
