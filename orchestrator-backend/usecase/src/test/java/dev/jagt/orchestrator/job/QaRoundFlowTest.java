package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.LinkRefusals;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class QaRoundFlowTest {

  private final AgentSessions sessions = mock(AgentSessions.class);

  @Test
  void movesADeployedTaskToInProgressAndHoldsItThereUntilTheSessionReportsNoChanges(
      @TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths);
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    FlowReports flow = new FlowReports(state, Clock.systemUTC());
    TaskStops stops =
        new TaskStops(
            ignored -> Arbiter.Profile.OFF,
            state,
            ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
            new StopAnnouncements(
                new Notifications(List.of(), state, Runnable::run),
                new StopLog(new JsonMapper(), paths)),
            Clock.systemUTC());
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            stops);
    StepRelay relay =
        new StepRelay(
            new SessionBrief(paths), sessions, door, new TaskTurns(state, Clock.systemUTC()));
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    AgentStatusReports reports =
        new AgentStatusReports(
            state,
            new LinkRefusals(new ReachLedger(), (session, line) -> "", state),
            flow,
            new HandBack(mock(WorktreeChanges.class), new Rounds(config, ignored -> false), config),
            config);
    QaVerdict verdict =
        new QaVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.NOT_MET, "GET /me answered 500")),
            "",
            "");

    new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
            relay,
            flow,
            stops,
            state)
        .apply("ABC-42", task, verdict);
    TaskState relayed = state.task("ABC-42").orElseThrow();
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "the endpoint is fine", "no_changes", null, Map.of()),
        "ABC-42");

    assertThat(relayed.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    TaskState answered = state.task("ABC-42").orElseThrow();
    assertThat(answered.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(answered.report()).isEqualTo(AgentReport.NO_CHANGES);
  }

  @Test
  void holdsDeployedWhereTheSessionDeclinesARoundAfterItsQuestionWasAnsweredInsideTheRound(
      @TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths);
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    FlowReports flow = new FlowReports(state, Clock.systemUTC());
    TaskStops stops =
        new TaskStops(
            ignored -> Arbiter.Profile.OFF,
            state,
            ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
            new StopAnnouncements(
                new Notifications(List.of(), state, Runnable::run),
                new StopLog(new JsonMapper(), paths)),
            Clock.systemUTC());
    CaseDoor door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
            stops);
    TaskTurns turns = new TaskTurns(state, Clock.systemUTC());
    StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, door, turns);
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    AgentStatusReports reports =
        new AgentStatusReports(
            state,
            new LinkRefusals(new ReachLedger(), (session, line) -> "", state),
            flow,
            new HandBack(mock(WorktreeChanges.class), new Rounds(config, ignored -> false), config),
            config);
    QaVerdict verdict =
        new QaVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.NOT_MET, "GET /me answered 500")),
            "",
            "");

    new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
            relay,
            flow,
            stops,
            state)
        .apply("ABC-42", task, verdict);
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "which environment?", "question", null, Map.of()),
        "ABC-42");
    turns.opened("ABC-42", "answered", "d2", TaskStatus.IN_PROGRESS);
    reports.report(
        new AgentStatusMessage("IN_PROGRESS", "the endpoint is fine", "no_changes", null, Map.of()),
        "ABC-42");

    assertThat(state.task("ABC-42").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
  }

  @Test
  void asksTheHumanThroughTheFlowWhereARowIsUnprovableAndKeepsTheStatusDeployed(
      @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    state.putTask("ABC-42", task);
    QaVerdict verdict =
        new QaVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(new QaVerdict.Row("login works", QaVerdict.Status.UNPROVABLE, "no login")),
            "",
            "");

    new CheckerOutcomes(
            RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
            mock(StepRelay.class),
            new FlowReports(state, Clock.systemUTC()),
            mock(TaskStops.class),
            state)
        .apply("ABC-42", task, verdict);

    TaskState asked = state.task("ABC-42").orElseThrow();
    assertThat(asked.status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(asked.report()).isEqualTo(AgentReport.QUESTION);
    assertThat(asked.message()).contains("QA could not prove");
  }

  @Test
  void refusesAVerdictAtTheDoorWhereTheTaskWasClosedWhileQaRead(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.DEPLOYED)
            .alias("a1")
            .build()
            .withDeployment(
                new Deployment(
                    1,
                    List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                    1,
                    "digest"));
    QaVerdict verdict =
        new QaVerdict(
            "proj@0123456789abcdef",
            "",
            List.of(
                new QaVerdict.Row("login works", QaVerdict.Status.NOT_MET, "GET /me answered 500")),
            "",
            "");
    StepRelay relay = mock(StepRelay.class);

    QaEnding ending =
        new CheckerOutcomes(
                RoleDefinitions.fromFile(Path.of("../../roles/qa/role.yml.dist")),
                relay,
                new FlowReports(state, Clock.systemUTC()),
                mock(TaskStops.class),
                state)
            .apply("ABC-42", task, verdict);

    assertThat(ending).isEqualTo(QaEnding.GONE);
    verifyNoInteractions(relay);
  }
}
