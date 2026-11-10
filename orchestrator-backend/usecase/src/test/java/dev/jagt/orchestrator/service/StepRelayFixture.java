package dev.jagt.orchestrator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

public final class StepRelayFixture {

  public final CaseDoorFixture doors;
  public final CaseDoor door;
  public final TaskTurns turns;
  public final StepRelay relay;

  public StepRelayFixture(
      CaseDoorFixture doors, AgentSessions sessions, CaseDoor door, Clock clock) {
    this.doors = doors;
    this.door = door;
    turns = new TaskTurns(doors.state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    relay = new StepRelay(new SessionBrief(doors.paths), sessions, door, turns);
  }

  public StepRelayFixture(CaseDoorFixture doors, AgentSessions sessions) {
    this(doors, sessions, doors.door, Clock.systemUTC());
  }

  public StepRelayFixture(Path root, AgentSessions sessions, Clock clock) {
    this(new CaseDoorFixture(root, clock, Arbiter.Profile.OFF), sessions);
  }

  public StepRelayFixture(Path root, AgentSessions sessions, Step step, boolean admitted) {
    this(
        new CaseDoorFixture(root, Clock.systemUTC(), Arbiter.Profile.OFF),
        sessions,
        admitting(step, admitted),
        Clock.systemUTC());
  }

  public void relayedVerifyRounds(String... facts) {
    for (String text : facts) {
      relay.relayRound("ABC-42", Step.VERIFY, text, relay.standing("ABC-42"));
    }
  }

  public StepRelayFixture writeBrief(Path root, String text) throws IOException {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), text);
    return this;
  }

  public StepRelayFixture writeCases(Path root, String text) throws IOException {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/cases.md"), text);
    return this;
  }

  private static CaseDoor admitting(Step step, boolean admitted) {
    CaseDoor door = mock(CaseDoor.class);
    when(door.opens("ABC-42", step))
        .thenReturn(new CaseDoor.Admission(admitted, TaskStatus.REVIEWED));
    return door;
  }
}
