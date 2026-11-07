package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CarriedBlockTest {

  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final AgentRuntime runtime = mock(AgentRuntime.class);

  @Test
  void carriesWhatTheBoardKnowsGitsHistoryAndTheConversationsEndBeneathTheInstructions() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-42", TaskStatus.IN_PROGRESS)
            .title("Add export")
            .message("two tests left")
            .build();
    when(changes.history(task, 40)).thenReturn("# proj\ncommits:\nabc123 Add export\n");
    when(runtime.conversationTail(Path.of("/wt/ABC-42"), 16_000))
        .thenReturn(Optional.of("human: go on"));
    when(runtime.sessionLogOf(Path.of("/wt/ABC-42")))
        .thenReturn(Optional.of(Path.of("/logs/old.jsonl")));

    String carried = new CarriedBlock(changes, runtime).after("now handle empty files", task);

    assertThat(carried)
        .isEqualTo(
            """
            now handle empty files

            ## Carried from the previous session

            ### Where it stood
            \u2014 Add export. On the board: IN_PROGRESS; your last report: two tests left

            ### Git
            # proj
            commits:
            abc123 Add export

            ### Its last words, newest last
            human: go on

            ### Whole transcript, to grep as a last resort
            /logs/old.jsonl
            """);
  }

  @Test
  void carriesTheReportAndHistoryButNoConversationWhereTheOldSessionKeptNoTranscript() {
    TaskState task =
        TaskState.builder("proj", "/wt/ABC-42", TaskStatus.IN_PROGRESS)
            .title("Add export")
            .message("two tests left")
            .build();
    when(changes.history(task, 40)).thenReturn("# proj\ncommits:\nabc123 Add export\n");
    when(runtime.sessionLogOf(Path.of("/wt/ABC-42"))).thenReturn(Optional.empty());
    when(runtime.conversationTail(Path.of("/wt/ABC-42"), 16_000)).thenReturn(Optional.empty());

    String carried = new CarriedBlock(changes, runtime).after("the round", task);

    assertThat(carried)
        .isEqualTo(
            """
            the round

            ## Carried from the previous session

            ### Where it stood
            \u2014 Add export. On the board: IN_PROGRESS; your last report: two tests left

            ### Git
            # proj
            commits:
            abc123 Add export

            """);
  }

  @Test
  void replacesTheBlockTheInstructionsAlreadyEndWithInsteadOfDoublingIt() {
    TaskState task = TaskState.builder("proj", "/wt/ABC-42", TaskStatus.IN_PROGRESS).build();
    when(runtime.sessionLogOf(Path.of("/wt/ABC-42")))
        .thenReturn(Optional.of(Path.of("/logs/new.jsonl")));
    String held =
        "the round\n\n"
            + "## Carried from the previous session\n\n"
            + "### Whole transcript\n"
            + "/logs/old.jsonl\n";

    String carried = new CarriedBlock(changes, runtime).after(held, task);

    assertThat(carried).contains("/logs/new.jsonl").doesNotContain("/logs/old.jsonl");
  }
}
