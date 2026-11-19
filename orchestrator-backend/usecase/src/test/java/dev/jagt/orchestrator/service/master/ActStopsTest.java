package dev.jagt.orchestrator.service.master;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.master.ActStop.Cause;
import dev.jagt.orchestrator.service.stop.BoardStop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActStopsTest {

  @TempDir Path root;

  private ActStops stops;

  @BeforeEach
  void stopsOverTheTempRoot() {
    var paths = orchestratorPaths().withRoot(root).build();
    stops = new ActStops(new MasterBrake(paths), new BoardStop(paths, Clock.systemUTC()));
  }

  @Test
  void namesThreeBadEndingsWhereActStoppedFileStands() throws IOException {
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    assertThat(stops.standing()).contains(new ActStop(Cause.STRIKES, null));
  }

  @Test
  void liftsNothingWhereNoStopStands() {
    assertThat(stops.lift()).isFalse();
  }

  @Test
  void liftsTheStop() throws IOException {
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    boolean lifted = stops.lift();

    assertThat(lifted).isTrue();
    assertThat(stops.standing()).isEmpty();
  }

  @Test
  void namesTheBoardStopAndItsMomentWhereOnlyTheBoardIsStopped() throws IOException {
    Files.writeString(root.resolve("board-stopped"), "1000");

    assertThat(stops.standing()).contains(new ActStop(Cause.BOARD, 1000L));
  }

  @Test
  void namesNoStopWhereNothingIsStopped() {
    assertThat(stops.standing()).isEmpty();
  }

  @Test
  void namesTheBoardStopFirstWhereActIsStoppedToo() throws IOException {
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
    Files.writeString(root.resolve("board-stopped"), "1000");

    assertThat(stops.standing()).contains(new ActStop(Cause.BOARD, 1000L));
  }

  @Test
  void haltsWhereTheBoardIsStopped() throws IOException {
    Files.writeString(root.resolve("board-stopped"), "1000");

    assertThat(stops.halted()).isTrue();
  }

  @Test
  void haltsNothingWhereNothingIsStopped() {
    assertThat(stops.halted()).isFalse();
  }
}
