package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MasterPanelRolesTest {

  private static final String BRIEF =
      """
      | role | the question it asks | unless |
      |------|----------------------|--------|
      | architect | is the architecture held | |
      | developer | is the code right | code-reviewer |
      """;

  private static final String BOTH =
      """
      | role | the question it asks | applies | unless |
      |------|----------------------|---------|--------|
      | architect | is the architecture held | | |
      | QA | is it tested right | `!**/*.md` | code-reviewer |
      """;

  private static final String WORKTREE = "/wt/ABC-1-proj";

  private final ChargedReviews reviews = mock(ChargedReviews.class);
  private final MasterBriefs briefs = mock(MasterBriefs.class);
  private final RoundQuotes quotes = mock(RoundQuotes.class);
  private final RoleYields yields = mock(RoleYields.class);
  private final ConfigFile.ArbiterConfig judge =
      new ConfigFile.ArbiterConfig("judge", null, null, null, null);

  private final MasterReview stored = mock(MasterReview.class);

  private MasterPanel panel;
  private TaskState task;

  @BeforeEach
  void wirePanel() {
    panel = new MasterPanel(reviews, briefs, quotes, stored, yields);
    task = TaskState.builder("proj", WORKTREE, TaskStatus.REVIEW_PENDING).build();
    when(briefs.author()).thenReturn("");
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(BRIEF));
    when(quotes.round("ABC-1", task))
        .thenReturn(Optional.of(new MasterPanel.RoundRead("", "", "", List.of(), "")));
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE));
  }

  @Test
  void readsTheUnlessColumnOfEachRow() {
    assertThat(MasterPanel.roles(BRIEF, ""))
        .extracting(MasterPanel.Role::name, MasterPanel.Role::unless)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("architect", ""),
            org.assertj.core.groups.Tuple.tuple("developer", "code-reviewer"));
  }

  @Test
  void skipsRowWhereItYieldsToTheRoleItNames() {
    when(yields.yields("code-reviewer", task)).thenReturn(true);

    panel.review("ABC-1", task, judge);

    verify(reviews, never())
        .review(eq("ABC-1"), argThat(round -> round.prompt().contains("developer")));
    ArgumentCaptor<String> markdown = ArgumentCaptor.captor();
    verify(stored).write(eq("ABC-1"), eq(task), any(), markdown.capture());
    assertThat(markdown.getValue())
        .contains("# skipped [developer] the code-reviewer role reads it\n");
  }

  @Test
  void readsEveryRowWhereNoRowYields() {
    panel.review("ABC-1", task, judge);

    verify(reviews, times(2)).review(eq("ABC-1"), any());
  }

  @Test
  void skippedLineNamesTheRoleItYieldsToWhereRowHasBothAppliesAndUnless() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(BOTH));
    when(quotes.paths(task)).thenReturn(Optional.of(List.of("src/A.java")));
    when(yields.yields("code-reviewer", task)).thenReturn(true);

    panel.review("ABC-1", task, judge);

    ArgumentCaptor<String> markdown = ArgumentCaptor.captor();
    verify(stored).write(eq("ABC-1"), eq(task), any(), markdown.capture());
    assertThat(markdown.getValue()).contains("# skipped [QA] the code-reviewer role reads it\n");
  }

  @Test
  void skippedLineNamesTheGlobWhereRowHasBothAndTheRoleItYieldsToIsOff() {
    when(briefs.master(eq("ABC-1"), any())).thenReturn(Optional.of(BOTH));
    when(quotes.paths(task)).thenReturn(Optional.of(List.of("docs/A.md")));

    panel.review("ABC-1", task, judge);

    ArgumentCaptor<String> markdown = ArgumentCaptor.captor();
    verify(stored).write(eq("ABC-1"), eq(task), any(), markdown.capture());
    assertThat(markdown.getValue()).contains("# skipped [QA] no changed path matches !**/*.md\n");
  }
}
