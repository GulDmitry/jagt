package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.CaseDoorFixture;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.StepRelayFixture;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

final class MasterVerdictsFixture {

  final StateService state;
  final CaseDoor door;
  final StepRelay relay;
  final MasterClaims claims;

  MasterVerdictsFixture(Path root, AgentSessions sessions) throws IOException {
    CaseDoorFixture doors = new CaseDoorFixture(root, Clock.systemUTC(), Arbiter.Profile.OFF);
    state = doors.state;
    door = doors.door;
    relay = new StepRelayFixture(doors, sessions).relay;
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/master"));
    Files.copy(
        Path.of("../../roles/master/cases.md.dist"), root.resolve("roles/master/cases.md.dist"));
    claims =
        new MasterClaims(
            new FlowReports(
                state,
                Clock.systemUTC(),
                new Checkers(new TaskChart(Composition.builtIn())),
                new FlowRules(new TaskChart(Composition.builtIn()))));
  }

  void handBackFixed(int round) {
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", round));
  }

  void handBackPlan(int round, int why) {
    state.updateTask(
        "ABC-1",
        handedBack ->
            handedBack
                .withStatus(TaskStatus.REVIEW_PENDING, "plan", true, round)
                .withLastChangeOrigin(ActionOrigin.MCP)
                .withLastChangeWhy("plan " + why));
  }
}
