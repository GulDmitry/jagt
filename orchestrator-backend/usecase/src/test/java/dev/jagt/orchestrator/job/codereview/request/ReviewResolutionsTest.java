package dev.jagt.orchestrator.job.codereview.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewResolutionsTest {

  @TempDir Path worktree;

  private final StateService state = mock(StateService.class);
  private final ReviewResolutions resolutions = new ReviewResolutions(state);

  @Test
  void blockLinkingTheNoteResolvesItAsTheSessionsAnswer() throws IOException {
    Files.writeString(
        worktree.resolve("review_replies.md"),
        """
        ## https://h/a/b/pull/7#discussion_r21
        > Null here
        FIXED - guarded now

        ## https://h/a/b/pull/7#issuecomment-30
        > Rename it
        NO CHANGE - the name is the spec's

        ## a thread with no link
        > Odd
        QUESTION - which one?
        """);
    when(state.task("t1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("shop", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));

    Map<String, String> answered =
        resolutions.answered(LedgerRow.of("https://h/a/b/pull/7", "shop", "t1"));

    assertThat(answered).containsOnly(Map.entry("21", "fixed"), Map.entry("30", "no-change"));
  }

  @Test
  void requestNoTaskHoldsHasNoAnswers() {
    assertThat(resolutions.answered(LedgerRow.of("https://h/a/b/pull/7", "shop", ""))).isEmpty();
  }

  @Test
  void numberDropsTheDriversPrefix() {
    assertThat(ReviewResolutions.number("issue-30")).isEqualTo("30");
    assertThat(ReviewResolutions.number("21")).isEqualTo("21");
  }
}
