package dev.jagt.orchestrator.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.task.ReviewFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

public final class RoundRelayFixture {

  public final CaseDoorFixture doors;
  public final RoundRelay relay;
  private final AgentSessions sessions;
  private final Clock clock;

  public RoundRelayFixture(Path root, AgentSessions sessions, Clock clock, String dist)
      throws IOException {
    doors = new CaseDoorFixture(root, clock, Arbiter.Profile.OFF);
    this.sessions = sessions;
    this.clock = clock;
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(Path.of("../../roles/session/" + dist), root.resolve("roles/session/" + dist));
    relay = over(doors.stops);
  }

  public RoundRelay over(TaskStops stops) {
    return new RoundRelay(
        stops,
        new StepRelayFixture(doors, sessions, doors.door, clock).relay,
        doors.door,
        new SessionBrief(doors.paths));
  }

  public static TaskStops relayingStops() {
    TaskStops stops = mock(TaskStops.class);
    when(stops.round(anyString(), any(), anyString(), anyString()))
        .thenReturn(TaskStops.Relay.RELAY);
    when(stops.sinceLast(anyString(), any())).thenReturn("");
    return stops;
  }

  public static ReviewFacts facts(String pipeline, String failure, List<String> threads) {
    return new ReviewFacts(
        new ReviewFacts.Request(true, false, false, false, 0),
        new ReviewFacts.Pipeline(pipeline, failure, ""),
        new ReviewFacts.Discussion(threads, List.of(), List.of()));
  }
}
