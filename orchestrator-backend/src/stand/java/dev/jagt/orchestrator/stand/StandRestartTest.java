package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StandRestartTest {

  @Test
  void restartedJagtBindsTheFirstJagtsPortAndTheMarkerKeepsIt() throws Exception {
    Path recording =
        Path.of(StandRestartTest.class.getResource("/recordings/happy-path.jsonl").toURI());
    try (StandRun stand = StandRun.replay("restart-keeps-port", StandMode.ACT, recording)) {
      StandMarker first = StandMarker.read(stand.root(), 0);

      stand.restartJagt();

      StandMarker second = StandMarker.read(stand.root(), 0);
      assertThat(second.jagtPid()).isNotEqualTo(first.jagtPid());
      assertThat(second.port()).isEqualTo(first.port());
      stand.passed();
    }
  }

  @Test
  void sessionWindowIsStillListedWhenJagtRestartsMidTurn() throws Exception {
    Path recording =
        Path.of(StandRestartTest.class.getResource("/recordings/happy-path.jsonl").toURI());
    AtomicReference<String> before = new AtomicReference<>("");
    AtomicReference<String> after = new AtomicReference<>("");
    try (StandRun stand = StandRun.replay("restart-keeps-window", StandMode.ACT, recording)) {
      WorldEvent restart =
          world -> {
            if (world.relaysSeen() < 1) {
              return false;
            }
            before.set(stand.windows());
            world.restart();
            after.set(stand.windows());
            return true;
          };

      stand.drive(Script.to("ABC-1", "DEPLOYED").within(Duration.ofMinutes(5)).with(restart));

      assertThat(before.get()).isNotBlank();
      assertThat(after.get()).isEqualTo(before.get());
      stand.passed();
    }
  }
}
