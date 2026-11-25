package dev.jagt.orchestrator.service.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.testing.FakeHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugReadsTest {

  @Test
  void returnsTheLastLinesOfTheStopRecord(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("records"));
    Files.writeString(root.resolve("records/stops.jsonl"), "one\ntwo\nthree\n");

    assertThat(
            new DebugReads(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withData(root.toString())
                            .withStateFile(root.resolve("state.json").toString()),
                        FakeHost.machine()),
                    mock(StateService.class),
                    mock(AgentRuntime.class))
                .stops(2))
        .isEqualTo("two\nthree");
  }

  @Test
  void returnsTheLinesOfRecordHoldingMalformedByte(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("records"));
    Files.write(
        root.resolve("records/stops.jsonl"),
        new byte[] {
          'o', 'n', 'e', '\n', 't', 'w', (byte) 0xC3, '\n', 't', 'h', 'r', 'e', 'e', '\n'
        });

    assertThat(
            new DebugReads(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withData(root.toString())
                            .withStateFile(root.resolve("state.json").toString()),
                        FakeHost.machine()),
                    mock(StateService.class),
                    mock(AgentRuntime.class))
                .stops(3))
        .isEqualTo("one\ntw�\nthree");
  }

  @Test
  void returnsTheReviewedStopsBesideTheStateFile(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("records"));
    Files.writeString(root.resolve("records/stops-reviewed.jsonl"), "verdict\n");

    assertThat(
            new DebugReads(
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withData(root.toString())
                            .withStateFile(root.resolve("state.json").toString()),
                        FakeHost.machine()),
                    mock(StateService.class),
                    mock(AgentRuntime.class))
                .stopsReviewed(5))
        .isEqualTo("verdict");
  }

  @Test
  void answersEmptyAndCreatesNothingWhereTheRecordDoesNotExistYet(@TempDir Path root)
      throws IOException {
    String read =
        new DebugReads(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults()
                        .withRoot(root.toString())
                        .withData(root.toString())
                        .withStateFile(root.resolve("state.json").toString()),
                    FakeHost.machine()),
                mock(StateService.class),
                mock(AgentRuntime.class))
            .stops(5);

    try (Stream<Path> left = Files.list(root)) {
      assertThat(read).isEmpty();
      assertThat(left.toList()).isEqualTo(List.of());
    }
  }

  @Test
  void leavesTheRecordAndTheSessionUntouchedByAnyRead(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("records"));
    Files.writeString(root.resolve("records/stops.jsonl"), "one\ntwo\n");
    StateService state = mock(StateService.class);
    AgentRuntime runtime = mock(AgentRuntime.class);
    DebugReads reads =
        new DebugReads(
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withData(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()),
            state,
            runtime);

    reads.stops(1);
    reads.stopsReviewed(1);

    assertThat(Files.readString(root.resolve("records/stops.jsonl"))).isEqualTo("one\ntwo\n");
    verifyNoInteractions(state, runtime);
  }
}
