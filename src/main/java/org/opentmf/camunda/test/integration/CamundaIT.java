package org.opentmf.camunda.test.integration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Declares a Camunda integration test: the one supported way to put {@code @SpringBootTest} on a
 * class that extends {@link BaseBpmIT}.
 *
 * <pre>{@code
 * @CamundaIT
 * class MyBpmFlowIT extends BaseBpmIT { ... }
 * }</pre>
 *
 * <p>Why one annotation: every integration test class must declare IDENTICAL {@code
 * SpringBootTest} attributes, so that Spring's TestContext cache serves ONE application context —
 * one engine, one job executor. Two classes with different attributes boot two contexts; both
 * share the same Testcontainers database (an identical {@code jdbc:tc} URL reuses the container),
 * and the first context's still-live job executor races the second class's jobs, which shows up
 * as intermittent, confusing failures. With every class on {@code @CamundaIT} the attributes
 * cannot diverge.
 *
 * <p>It carries {@code webEnvironment = DEFINED_PORT}: the embedded engine's REST API listens on
 * the configured {@code server.port}, where the external-task client under test reaches it. Put
 * the port and other settings in the test {@code application.yml}, not on the annotation.
 *
 * <p>Additive: a class that still declares {@code @SpringBootTest(webEnvironment =
 * DEFINED_PORT)} itself keeps working and shares the same context.
 *
 * @see BaseBpmIT
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public @interface CamundaIT {}
