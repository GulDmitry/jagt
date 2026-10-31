package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.RoleRecord;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QaOutcomesTest {

    @Test
    void marksTheChangePassedWhileItsShaIsTheDeployTip(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build().withDeployment(new Deployment(1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, "digest"));
        state.putTask("ABC-42", task);
        QaVerdict verdict = new QaVerdict("proj@0123456789abcdef", "", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.MET, "GET /me 200")), "", "");

        QaEnding ending = new CheckerOutcomes(RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")), mock(StepRelay.class), new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class), state).apply("ABC-42", task, verdict);

        assertThat(ending).isEqualTo(QaEnding.PASSED);
        assertThat(state.task("ABC-42").orElseThrow().passedBy(Checkers.QA)).isTrue();
    }

    @Test
    void dropsAVerdictOnAShaThatIsNoLongerTheDeployTip(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build().withDeployment(new Deployment(1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("fedcba9876543210")), 1, "digest"));
        state.putTask("ABC-42", task);
        QaVerdict verdict = new QaVerdict("proj@0123456789abcdef", "", List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.MET, "GET /me 200")), "", "");

        QaEnding ending = new CheckerOutcomes(RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")), mock(StepRelay.class), new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class), state).apply("ABC-42", task, verdict);

        assertThat(ending).isEqualTo(QaEnding.DROPPED);
        assertThat(state.task("ABC-42").orElseThrow().passedBy(Checkers.QA)).isFalse();
    }

    @Test
    void showsNoPassedLabelOnceALaterDeployMovedTheTip(@TempDir Path root) {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build().withDeployment(new Deployment(1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, "digest"))
                .withRole(Checkers.QA, RoleRecord.defaults().withPassed("proj@0123456789abcdef"));

        TaskState later = task.withDeployment(new Deployment(2,
                List.of(Deployment.Target.of("proj", "dev").withMerge("fedcba9876543210")), 1, "next"));

        assertThat(task.passedBy(Checkers.QA)).isTrue();
        assertThat(later.passedBy(Checkers.QA)).isFalse();
    }
}
