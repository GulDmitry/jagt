package dev.jagt.orchestrator.capability.done;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentSpend;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FinishedArtifactsTest {

  @TempDir Path root;

  private OrchestratorPaths paths;
  private StateService state;
  private ConfigService config;
  private Path worktree;
  private TaskState task;
  private FinishedArtifacts artifacts;

  @BeforeEach
  void finishedTaskWithAnEmptyWorktree() throws Exception {
    paths = orchestratorPaths().withRoot(root).build();
    state = stateService().withRoot(root).build();
    config =
        new ConfigService(
            paths,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())));
    worktree = root.resolve("ABC-42-proj");
    Files.createDirectories(worktree);
    task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
            .alias("a1")
            .title("a thing")
            .build();
    state.putTask("ABC-42", task);
    artifacts =
        new FinishedArtifacts(
            state,
            paths,
            Clock.fixed(Instant.parse("2026-10-28T10:15:30Z"), ZoneOffset.UTC),
            config,
            new JsonMapper());
  }

  @Test
  void keepsTheDocumentsAndTheRecordOfFinishedTaskWhereHumanCanStillReadThem() throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    Files.writeString(worktree.resolve("plan.md"), "the plan");
    Files.writeString(worktree.resolve("review_replies.md"), "the replies");
    TaskState spent =
        TaskState.builder("proj", worktree.toString(), TaskStatus.APPROVED)
            .alias("a1")
            .title("a thing")
            .usage(TokenUsage.ofCall(10, 0, 10, 0.5))
            .agentSpend(
                new AgentSpend(TokenUsage.ofCall(30, 5, 20, 2.0), Map.of("/logs/a.jsonl", 9L)))
            .build();
    state.putTask("ABC-42", spent);

    artifacts.keep(FinishedTask.of("ABC-42", spent, 1L));

    Path kept = root.resolve("archive/261028-101530-ABC-42");
    assertThat(kept.resolve("plan.md")).hasContent("the plan");
    assertThat(kept.resolve("review_replies.md")).hasContent("the replies");
    assertThat(Files.readString(kept.resolve("finished.json")))
        .contains("\"id\"")
        .contains("ABC-42")
        .contains("\"usage\"")
        .contains("\"agentUsage\"")
        .contains("/logs/a.jsonl");
  }

  @Test
  void keepsTheRecordOfTaskThatLeftNoDocumentBehind() throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");

    artifacts.keep(FinishedTask.of("ABC-42", task, 1L));

    Path kept = root.resolve("archive/261028-101530-ABC-42");
    assertThat(kept.resolve("finished.json")).exists();
    assertThat(kept.resolve("plan.md")).doesNotExist();
  }

  @Test
  void keepsLineNamingDocumentPastTheSizeLimitInsteadOfTheDocument() throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    Files.write(worktree.resolve("plan.md"), new byte[10 * 1024 * 1024 + 1]);

    artifacts.keep(FinishedTask.of("ABC-42", task, 1L));

    Path kept = root.resolve("archive/261028-101530-ABC-42");
    assertThat(kept.resolve("plan.md")).doesNotExist();
    assertThat(kept.resolve("plan.md.not-kept"))
        .hasContent("plan.md was 10485761 bytes, past the 10485760 kept");
  }

  @Test
  void keepsDocumentExactlyAsLargeAsTheSizeLimit() throws Exception {
    try (RandomAccessFile plan = new RandomAccessFile(worktree.resolve("plan.md").toFile(), "rw")) {
      plan.setLength(10L * 1024 * 1024);
    }
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  archive:\n    maxMegabytes: 50\n");

    artifacts.keep(FinishedTask.of("ABC-42", task, 1L));

    Path kept = root.resolve("archive/261028-101530-ABC-42");
    assertThat(kept.resolve("plan.md")).hasSize(10L * 1024 * 1024);
    assertThat(kept.resolve("plan.md.not-kept")).doesNotExist();
  }

  @Test
  void keepsEverySnapshotOfTaskDoneTwiceTheSameDay() throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    Files.writeString(worktree.resolve("plan.md"), "the first plan");
    new FinishedArtifacts(
            state,
            paths,
            Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC),
            config,
            new JsonMapper())
        .keep(FinishedTask.of("ABC-42", task, 1L));
    Files.writeString(worktree.resolve("plan.md"), "the second plan");

    new FinishedArtifacts(
            state,
            paths,
            Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC),
            config,
            new JsonMapper())
        .keep(FinishedTask.of("ABC-42", task, 2L));

    assertThat(root.resolve("archive/261028-090000-ABC-42/plan.md")).hasContent("the first plan");
    assertThat(root.resolve("archive/261028-150000-ABC-42/plan.md")).hasContent("the second plan");
  }

  @Test
  void dropsTheOldestSnapshotAndKeepsTheNewestOncePastTheByteBudget() throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  archive:\n    maxMegabytes: 1\n");
    Files.createDirectories(root.resolve("archive/260101-000000-ABC-1"));
    Files.write(root.resolve("archive/260101-000000-ABC-1/plan.md"), new byte[700 * 1024]);
    Files.write(worktree.resolve("plan.md"), new byte[700 * 1024]);

    artifacts.keep(FinishedTask.of("ABC-42", task, 1L));

    assertThat(root.resolve("archive/260101-000000-ABC-1")).doesNotExist();
    assertThat(root.resolve("archive/261028-101530-ABC-42/plan.md")).exists();
  }

  @Test
  void dropsAnOlderZipBeforeAnySnapshotOncePastTheByteBudget() throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  archive:\n    maxMegabytes: 1\n");
    Files.createDirectories(root.resolve("archive/260101-000000-ABC-1"));
    Files.write(root.resolve("archive/260101-000000-ABC-1/plan.md"), new byte[400 * 1024]);
    Files.write(root.resolve("archive/archive-20250101-0000.zip"), new byte[700 * 1024]);

    artifacts.keep(FinishedTask.of("ABC-42", task, 1L));

    assertThat(root.resolve("archive/archive-20250101-0000.zip")).doesNotExist();
    assertThat(root.resolve("archive/260101-000000-ABC-1/plan.md")).exists();
  }
}
