package dev.jagt.orchestrator.stand.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WorktreeSnapshotsTest {

  @TempDir Path root;

  private Path worktree;
  private JsonNode state;

  @BeforeEach
  void placeWorktree() throws Exception {
    worktree = Files.createDirectories(root.resolve("work"));
    state =
        JsonMapper.builder()
            .build()
            .readTree(
                "{\"tasks\":{\"ABC-42\":{\"repos\":[{\"worktreePath\":\""
                    + worktree.toString().replace("\\", "\\\\")
                    + "\"}]}}}");
  }

  @Test
  void sessionFileIsSnapshottedWithItsTaskAndContent() throws Exception {
    Files.writeString(worktree.resolve("task_notes.md"), "notes");

    new WorktreeSnapshots(root).take(state);

    List<String> lines = Files.readAllLines(root.resolve("snapshots.jsonl"));
    assertThat(lines).singleElement().asString().contains("\"task\":\"ABC-42\"");
    assertThat(lines.get(0))
        .contains("\"file\":\"task_notes.md\"")
        .contains("\"content\":\"notes\"");
  }

  @Test
  void anUnchangedFileIsSnapshottedOnce() throws Exception {
    Files.writeString(worktree.resolve("task_notes.md"), "notes");
    WorktreeSnapshots snapshots = new WorktreeSnapshots(root);

    snapshots.take(state);
    snapshots.take(state);

    assertThat(Files.readAllLines(root.resolve("snapshots.jsonl"))).hasSize(1);
  }

  @Test
  void changedFileIsSnapshottedAgain() throws Exception {
    Files.writeString(worktree.resolve("task_notes.md"), "one");
    WorktreeSnapshots snapshots = new WorktreeSnapshots(root);
    snapshots.take(state);
    Files.writeString(worktree.resolve("task_notes.md"), "two");

    snapshots.take(state);

    assertThat(Files.readAllLines(root.resolve("snapshots.jsonl"))).hasSize(2);
  }

  @Test
  void theStoredVerdictIsSnapshottedAsParsedReview() throws Exception {
    Files.createDirectories(worktree.resolve(".jagt"));
    Files.writeString(worktree.resolve(".jagt/master-review.json"), "{\"round\":2}");

    new WorktreeSnapshots(root).take(state);

    assertThat(Files.readString(root.resolve("snapshots.jsonl")))
        .contains("\"review\":{\"round\":2}");
  }

  @Test
  void taskWithoutWorktreeIsSkipped() {
    JsonNode bare = JsonMapper.builder().build().readTree("{\"tasks\":{\"ABC-42\":{}}}");

    new WorktreeSnapshots(root).take(bare);

    assertThat(root.resolve("snapshots.jsonl")).doesNotExist();
  }
}
