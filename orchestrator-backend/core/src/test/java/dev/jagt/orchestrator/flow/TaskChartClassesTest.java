package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.statechart.StatechartInvalid;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskChartClassesTest {

  @Test
  void buildFailsNamingTheStateWhereComposedRoleLeavesMoveWithoutClass() {
    Composition composed = new Composition(List.of(new FakeGapRole()));

    assertThatThrownBy(() -> new TaskChart(composed))
        .isInstanceOfSatisfying(
            StatechartInvalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .contains("move Verified has no class in state gap.READING"));
  }
}
