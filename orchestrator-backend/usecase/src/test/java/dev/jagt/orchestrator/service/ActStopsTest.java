package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.ActStop.Cause;
import dev.jagt.orchestrator.service.master.MasterBrake;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActStopsTest {

  @Test
  void namesThreeBadEndingsWhereActStoppedFileStands(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

    assertThat(new ActStops(new MasterBrake(orchestratorPaths().withRoot(root).build())).standing())
        .contains(new ActStop(Cause.STRIKES));
  }

  @Test
  void liftsNothingWhereNoStopStands(@TempDir Path root) {
    assertThat(new ActStops(new MasterBrake(orchestratorPaths().withRoot(root).build())).lift())
        .isFalse();
  }

  @Test
  void liftsTheStop(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
    ActStops stops = new ActStops(new MasterBrake(orchestratorPaths().withRoot(root).build()));

    boolean lifted = stops.lift();

    assertThat(lifted).isTrue();
    assertThat(stops.standing()).isEmpty();
  }
}
