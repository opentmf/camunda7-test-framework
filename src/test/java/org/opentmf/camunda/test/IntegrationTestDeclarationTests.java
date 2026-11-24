package org.opentmf.camunda.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.opentmf.camunda.test.integration.BaseBpmIT;
import org.opentmf.camunda.test.integration.CamundaIT;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Every integration test of this repository is declared with {@code @CamundaIT}, so all of them
 * share ONE application context: two contexts would share one Testcontainers database and race
 * each other's job executors.
 */
@AnalyzeClasses(
    packages = "org.opentmf.camunda.test",
    importOptions = ImportOption.OnlyIncludeTests.class)
class IntegrationTestDeclarationTests {

  @ArchTest
  static final ArchRule integrationTests_areDeclaredWithCamundaIT =
      classes()
          .that()
          .areAssignableTo(BaseBpmIT.class)
          .and()
          .areNotAssignableFrom(BaseBpmIT.class)
          .and()
          .haveSimpleNameEndingWith("IT")
          .should()
          .beAnnotatedWith(CamundaIT.class)
          .andShould()
          .notBeAnnotatedWith(SpringBootTest.class);
}
