package dev.jagt.orchestrator.service.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.git.GitCommands;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorktreeCodeTest {

  @TempDir Path temp;

  private final StateService state = mock(StateService.class);
  private final GitCommands git = mock(GitCommands.class);
  private final WorktreeCode code = new WorktreeCode(state, git);
  private Path worktree;

  @BeforeEach
  void taskWithOneWorktree() throws Exception {
    worktree = Files.createDirectories(temp.resolve("ABC-42-alpha"));
    when(state.canonicalTaskId("ABC-42")).thenReturn("ABC-42");
    when(state.task("ABC-42"))
        .thenReturn(
            Optional.of(
                TaskState.builder("alpha", worktree.toString(), TaskStatus.IN_PROGRESS).build()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"../outside.txt", "src/../../outside.txt", "/etc/hosts", ".git", ""})
  void refusesPathLeavingTheWorktree(String path) throws Exception {
    Files.writeString(temp.resolve("outside.txt"), "secret");

    assertThatThrownBy(() -> code.file("ABC-42", "", path)).isInstanceOf(Refusal.class);
  }

  @Test
  void refusesSymlinkWhoseTargetIsOutsideTheWorktree() throws Exception {
    Files.writeString(temp.resolve("outside.txt"), "secret");
    Files.createSymbolicLink(worktree.resolve("link.txt"), temp.resolve("outside.txt"));

    assertThatThrownBy(() -> code.file("ABC-42", "", "link.txt")).isInstanceOf(Refusal.class);
  }

  @Test
  void searchNeverReadsSymlinkWhoseTargetIsOutsideTheWorktree() throws Exception {
    Files.writeString(temp.resolve("outside.txt"), "secret");
    Files.createSymbolicLink(worktree.resolve("link.txt"), temp.resolve("outside.txt"));
    when(git.run(any(), any())).thenReturn(new Processes.Result(0, "link.txt\0", ""));

    assertThat(code.search("ABC-42", "", "secret").hits()).isEmpty();
  }

  @Test
  void readsFileInsideTheWorktree() throws Exception {
    Files.createDirectories(worktree.resolve("src"));
    Files.writeString(worktree.resolve("src/A.java"), "class A {}\n");

    var opened = code.file("ABC-42", "", "src/A.java");

    assertThat(opened.kind()).isEqualTo(WorktreeCode.Kind.TEXT);
    assertThat(opened.text()).isEqualTo("class A {}\n");
  }

  @Test
  void answersBinaryWithoutTextForFileThatIsNotUtf8() throws Exception {
    Files.write(worktree.resolve("a.bin"), new byte[] {0, (byte) 0xff, (byte) 0xfe});

    var opened = code.file("ABC-42", "", "a.bin");

    assertThat(opened.kind()).isEqualTo(WorktreeCode.Kind.BINARY);
    assertThat(opened.text()).isEmpty();
  }

  @Test
  void answersTooLargeWithoutTextForFileOverTheCap() throws Exception {
    Files.writeString(worktree.resolve("big.txt"), "x".repeat(512 * 1024 + 1));

    var opened = code.file("ABC-42", "", "big.txt");

    assertThat(opened.kind()).isEqualTo(WorktreeCode.Kind.TOO_LARGE);
    assertThat(opened.text()).isEmpty();
  }

  @Test
  void searchNamesTheLineOfEachMatchIgnoringCase() throws Exception {
    Files.writeString(worktree.resolve("a.txt"), "one\nTwo needle\nthree\n");
    when(git.run(any(), any())).thenReturn(new Processes.Result(0, "a.txt\0", ""));

    var found = code.search("ABC-42", "", "NEEDLE");

    assertThat(found.hits()).containsExactly(new WorktreeCode.Hit("a.txt", 2, "Two needle"));
    assertThat(found.cut()).isFalse();
  }

  @Test
  void searchStopsAtTheCapAndSaysSo() throws Exception {
    Files.writeString(worktree.resolve("a.txt"), "needle\n".repeat(250));
    when(git.run(any(), any())).thenReturn(new Processes.Result(0, "a.txt\0", ""));

    var found = code.search("ABC-42", "", "needle");

    assertThat(found.hits()).hasSize(200);
    assertThat(found.cut()).isTrue();
  }

  @Test
  void treeListsTheFilesOfTheWorktreeSorted() {
    when(git.run(any(), any())).thenReturn(new Processes.Result(0, "b.txt\0a.txt\0", ""));

    var trees = code.tree("ABC-42");

    assertThat(trees).hasSize(1);
    assertThat(trees.getFirst().files()).containsExactly("a.txt", "b.txt");
  }
}
