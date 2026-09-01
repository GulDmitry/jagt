package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class FlowCheckTest {

    @Test
    void findsNoHoleInTheMachineJagtShipsWith() {
        assertThat(new FlowCheck().problems()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"NEW", "DONE"})
    void everyStatusCanBeReachedEitherByAnActionOrByTheTaskReportingIt(TaskStatus status) {
        assertThat(FlowRules.targets().contains(status) || FlowRules.reportable(status))
                .as("%s has a way in", status).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TaskAction.class)
    void everyVerbIsMentionedByTheTable(TaskAction action) {
        assertThat(FlowRules.mentions(action)).isTrue();
    }

    @Test
    void countsOnlyTheStatusesAnActionActuallyLeadsToAsTargets() {
        assertThat(FlowRules.targets()).contains(TaskStatus.CI_POLLING, TaskStatus.DEPLOYED, TaskStatus.REVERTED)
                .doesNotContain(TaskStatus.NEW, TaskStatus.DONE);
    }
}
