package dev.jagt.orchestrator.service.log;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.testing.FakeHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StopLogTest {

  @Test
  void appendsOneLineWithEveryFieldBesideTheStateFile(@TempDir Path root) throws IOException {
    StopLog log =
        new StopLog(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withData(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()));

    log.record(
        new StopLog.Stop(
            100,
            "ABC-42",
            "cart",
            "STALLED",
            TaskStatus.CI_POLLING,
            "3 rounds",
            new StopLog.Steps(List.of(), List.of())));

    assertThat(Files.readString(root.resolve("records/stops.jsonl")))
        .isEqualTo(
            "{\"at\":100,\"task\":\"ABC-42\",\"alias\":\"cart\",\"trigger\":\"STALLED\","
                + "\"status\":\"CI_POLLING\",\"evidence\":\"3 rounds\","
                + "\"steps\":{\"history\":[],\"trail\":[]}}\n");
  }

  @Test
  void carriesTheNewestTwentyStepsOfHistoryAndTrailNewestLast(@TempDir Path root)
      throws IOException {
    StopLog log =
        new StopLog(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withData(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()));
    List<StatusChange> history =
        LongStream.rangeClosed(1, 22)
            .mapToObj(at -> new StatusChange(TaskStatus.IN_PROGRESS, at, null))
            .toList();
    List<RoundTrail.Round> trail =
        LongStream.rangeClosed(1, 22)
            .mapToObj(at -> new RoundTrail.Round(at, 1, "", "d", ""))
            .toList();

    log.record(
        new StopLog.Stop(
            100,
            "ABC-42",
            "cart",
            "ROUNDS",
            TaskStatus.IN_PROGRESS,
            "x",
            new StopLog.Steps(history, trail)));

    JsonNode steps =
        new JsonMapper()
            .readTree(Files.readString(root.resolve("records/stops.jsonl")))
            .get("steps");
    assertThat(steps.get("history")).hasSize(20);
    assertThat(steps.get("history").get(0).get("at").asLong()).isEqualTo(3);
    assertThat(steps.get("history").get(19).get("at").asLong()).isEqualTo(22);
    assertThat(steps.get("trail")).hasSize(20);
    assertThat(steps.get("trail").get(19).get("at").asLong()).isEqualTo(22);
  }
}
