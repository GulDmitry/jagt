package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoorFixture;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

final class ConflictHandOffs {

  final StateService state;
  final ConflictHandOff handOff;
  private final Path root;

  ConflictHandOffs(
      Path root, AgentSessions sessions, ConflictStall stall, ConflictFinish finish) {
    this.root = root;
    CaseDoorFixture fixture = new CaseDoorFixture(root, Clock.systemUTC(), Arbiter.Profile.OFF);
    state = fixture.state;
    StepRelay relay =
        new StepRelay(
            new SessionBrief(fixture.paths),
            sessions,
            fixture.door,
            new TaskTurns(
                state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn()))));
    handOff = new ConflictHandOff(relay, new ConflictHandover(sessions, relay), stall, finish);
  }

  ConflictHandOffs seeded(TaskStatus status) throws IOException {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", status).build());
    return this;
  }

  static DeployConflicts.WaitingConflict deployConflict(Standing standing) {
    return new DeployConflicts.WaitingConflict(
        Path.of("/src/ABC-42-deploy"),
        false,
        new Conflict.Progress(7, 0, "d"),
        null,
        false,
        standing);
  }

  static DeployConflicts.WaitingConflict stagedInFull() {
    return new DeployConflicts.WaitingConflict(
        Path.of("/src/ABC-42-deploy"),
        true,
        new Conflict.Progress(0, 7, "d"),
        null,
        false,
        Standing.ABSENT);
  }

  static DeployConflicts.WaitingConflict redPipeline() {
    return new DeployConflicts.WaitingConflict(
        Path.of("/src/ABC-42-deploy"),
        false,
        new Conflict.Progress(0, 0, "d"),
        "red pipeline on the deploy commit: proj 01234567 https://git.example.invalid/p/7",
        false,
        new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0));
  }

  static DeployConflicts.WaitingConflict revertConflict() {
    return new DeployConflicts.WaitingConflict(
        Path.of("/src/ABC-42-revert"),
        false,
        new Conflict.Progress(2, 0, "r"),
        null,
        true,
        new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0));
  }
}
