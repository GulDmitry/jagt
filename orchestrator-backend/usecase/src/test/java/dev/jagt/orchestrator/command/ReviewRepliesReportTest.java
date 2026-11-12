package dev.jagt.orchestrator.command;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReviewDrafts;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewRepliesReportTest {

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private StateService state;
  private ReviewRepliesReport report;

  @BeforeEach
  void reportReadsTheDraftsOfTheTasksInTheState() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    state = stateService().withRoot(root).build();
    report =
        new ReviewRepliesReport(
            new TaskViews(
                state,
                config,
                new Rounds(
                    config,
                    ignored -> false,
                    () -> false,
                    () -> false,
                    new Checkers(new TaskChart(Composition.builtIn()))),
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))),
            state,
            new ReviewDrafts(config));
  }

  @Test
  void showsEachCommentWithTheVerdictAndTheReplyThatWillBeSentForIt() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(
        worktree.resolve("review_replies.md"),
        """
        ## !12 thread 1
        > extract the grid cases into a separate parameterized test
        NO CHANGE - buildsTheGrid already is one, and its cases live in grids().

        ## src/Grid.java:56
        > the canonical row count is wrong
        FIXED - Measured it and pinned the count.
        """);
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());

    String out = report.render("a1");

    assertThat(out)
        .contains(
            "  1 · NO CHANGE · !12 thread 1",
            "      > extract the grid cases into a separate parameterized test",
            "      buildsTheGrid already is one, and its cases live in grids().",
            "  2 · FIXED · src/Grid.java:56");
  }

  @Test
  void saysSoWhenTheRoundThoseRepliesWereWrittenForIsAlreadyShipped() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.setLastModifiedTime(
        Files.writeString(
            worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n"),
        java.nio.file.attribute.FileTime.fromMillis(1_000));
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .mrCreatedAt(2_000)
            .build());

    String out = report.render("a1");

    assertThat(out).contains("drafted in a round already shipped", "Renamed it.");
  }

  @Test
  void keepsWhatTheAgentWroteOutsideThePrescribedShape() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(
        worktree.resolve("review_replies.md"),
        "the bot reviewed a stale diff of that file\n\n## thread 1\nFIXED - Renamed it.\n");
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());

    String out = report.render("a1");

    assertThat(out).contains("the bot reviewed a stale diff of that file");
  }

  @Test
  void marksBlockWhoseVerdictTheAgentLeftOutRatherThanDroppingIt() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(
        worktree.resolve("review_replies.md"),
        "## thread 1\n> the canonical row count is wrong\nMeasured it two rounds ago.\n");
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());

    String out = report.render("a1");

    assertThat(out).contains("  1 · no verdict · thread 1", "      Measured it two rounds ago.");
  }

  @Test
  void saysNothingIsPostedUntilTheRoundIsShipped() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Widget layout is off")
            .build());

    String out = report.render("a1");

    assertThat(out)
        .startsWith(
            "review replies drafted for a1 · Widget layout is off — nothing is posted until `ship"
                + " a1`");
  }

  @Test
  void saysSoWhenTheNamedTaskHasDraftedNothing() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("wt").toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());

    String out = report.render("a1");

    assertThat(out)
        .isEqualTo("a1 has no drafted replies — review_replies.md is not in its worktree.");
  }

  @Test
  void refusesTaskItDoesNotKnow() {

    String out = report.render("zz");

    assertThat(out).isEqualTo("no task `zz`.");
  }

  @Test
  void namedNoTaskItShowsEveryRoundThatIsHoldingDrafts() throws IOException {
    Path drafting = Files.createDirectories(root.resolve("wt1"));
    Files.writeString(drafting.resolve("review_replies.md"), "## thread 1\nFIXED - Renamed it.\n");
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", drafting.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder("alpha", root.resolve("wt2").toString(), TaskStatus.IN_PROGRESS)
            .alias("a2")
            .build());

    String out = report.render("");

    assertThat(out).contains("review replies drafted for a1").doesNotContain("a2");
  }

  @Test
  void saysWhenNoRoundAnywhereHasDraftedAnything() {
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", root.resolve("wt").toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());

    String out = report.render(null);

    assertThat(out)
        .isEqualTo("no drafted review replies: no task is carrying a review_replies.md.");
  }

  @Test
  void showsOnlyTheNamedTasksDraftsAndNoBlankLineBetweenTheHeaderAndTheFirstComment()
      throws IOException {
    Path first = Files.createDirectories(root.resolve("wt1"));
    Path second = Files.createDirectories(root.resolve("wt2"));
    Files.writeString(
        first.resolve("review_replies.md"),
        """
        ## src/One.java:1
        > first task comment
        FIXED - done in the first.
        """);
    Files.writeString(
        second.resolve("review_replies.md"),
        """
        ## src/Two.java:2
        > second task comment
        FIXED - done in the second.
        """);
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", first.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());
    state.putTask(
        "ABC-2",
        TaskState.builder("alpha", second.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a2")
            .build());

    String out = report.render("a2");

    assertThat(out)
        .contains("nothing is posted until `ship a2`\n\n  1 · FIXED · src/Two.java:2")
        .doesNotContain("first task comment");
  }

  @Test
  void saysNoRoundIsHoldingDraftsWhereTheFileHoldsOnlyBlankLines() throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(worktree.resolve("review_replies.md"), "  \n\n");
    state.putTask(
        "ABC-1",
        TaskState.builder("alpha", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build());

    String out = report.render("");

    assertThat(out)
        .isEqualTo("no drafted review replies: no task is carrying a review_replies.md.");
  }
}
