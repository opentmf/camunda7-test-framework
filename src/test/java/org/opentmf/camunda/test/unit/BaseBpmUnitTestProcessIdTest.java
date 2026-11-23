package org.opentmf.camunda.test.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaseBpmUnitTestProcessIdTest {

  private static final class Probe extends BaseBpmUnitTest {}

  @Test
  void eachInstanceGetsItsOwnProcessId() {
    assertThat(new Probe().camundaProcessId).isNotEqualTo(new Probe().camundaProcessId);
  }

  @Test
  void processIdIsTenNumericDigits() {
    assertThat(new Probe().camundaProcessId).matches("\\d{10}");
  }

  @Test
  void processIdsIncreaseInCreationOrder() {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      ids.add(Long.parseLong(new Probe().camundaProcessId));
    }
    assertThat(ids).isSorted().doesNotHaveDuplicates();
  }

  @Test
  void builtExternalTaskCarriesTheProcessId() {
    Probe probe = new Probe();
    assertThat(probe.buildExternalTask(new HashMap<>()).getProcessInstanceId())
        .isEqualTo(probe.camundaProcessId);
  }
}
