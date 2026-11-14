package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FlowCheckTest {

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  @Test
  void findsNoHoleInTheMachineJagtShipsWith() {
    assertThat(new FlowCheck(rules).problems()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"NEW", "DONE"})
  void everyStatusCanBeReachedByAnActionReportOrRoundRead(TaskStatus status) {
    assertThat(
            rules.targets().contains(status)
                || FlowRules.reportable(status)
                || rules.redirects().contains(status)
                || FlowRules.reads().contains(status))
        .as("%s has a way in", status)
        .isTrue();
  }

  @ParameterizedTest
  @EnumSource(TaskAction.class)
  void everyVerbIsMentionedByTheTable(TaskAction action) {
    assertThat(FlowRules.mentions(action)).isTrue();
  }

  @Test
  void countsOnlyTheStatusesAnActionActuallyLeadsToAsTargets() {
    assertThat(rules.targets())
        .contains(TaskStatus.CI_POLLING, TaskStatus.DEPLOYED, TaskStatus.REVERTED)
        .doesNotContain(TaskStatus.NEW, TaskStatus.DONE);
  }
}
