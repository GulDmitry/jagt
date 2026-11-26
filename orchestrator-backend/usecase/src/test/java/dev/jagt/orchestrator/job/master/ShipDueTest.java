package dev.jagt.orchestrator.job.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.review.ReviewDrafts;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.Turn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ShipDueTest {

  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final ReviewDrafts drafts = mock(ReviewDrafts.class);
  private final TaskStops stops = mock(TaskStops.class);

  @Test
  void isNotDueForHandBackWhoseBranchIsPushedUnchangedAndDraftsNothing() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void isDueForHandBackCarryingNewWork() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void isDueForHandBackWhoseBranchIsUnchangedButDraftsReplies() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(drafts.drafted(task)).thenReturn(true);
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void isNotDueWhileDraftsWaitThatNoShipWillPost() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(drafts.drafted(task)).thenReturn(true);
    when(drafts.heldForHuman(task)).thenReturn(true);
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void isNotDueWhereTheLoopStopForbidsAnotherShip() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(changes.anythingNewToShip(task)).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void shipPressedOnThisVeryTreeIsNotDueAgain() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;"));
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(changes.state(task)).thenReturn(Optional.of("tree-1;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void treeChangedSinceTheLastPressIsDue() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;"));
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(changes.state(task)).thenReturn(Optional.of("tree-2;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void roundAnsweredByNewDraftsOnTheSameTreeIsDueAgain() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;"));
    when(drafts.drafted(task)).thenReturn(true);
    when(drafts.unspentDigest(task)).thenReturn(Optional.of("d2"));
    when(changes.state(task)).thenReturn(Optional.of("tree-1;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void theSameTreeAndTheSameDraftsAreNotDueAgain() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;|drafts:d1"));
    when(drafts.drafted(task)).thenReturn(true);
    when(drafts.unspentDigest(task)).thenReturn(Optional.of("d1"));
    when(changes.state(task)).thenReturn(Optional.of("tree-1;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  static Stream<Arguments> cells() {
    String person = "## http://mr/1#note_1\nAuthor: person alice\n> rename\nFIXED - Done.\n";
    String bot = "## http://mr/1#note_2\nAuthor: bot review-bot\n> typo\nFIXED - Done.\n";
    String reviewer = "## http://mr/1#note_3\nAuthor: reviewer\n> leak\nFIXED - Done.\n";
    return Stream.of(
        Arguments.of("P1 replies off", false, false, person, true, false),
        Arguments.of("P2 person, Master unasked", true, false, person, true, false),
        Arguments.of("P3 person, Master may reply", true, true, person, true, true),
        Arguments.of("B1 bot only, Master unasked", true, false, bot, true, true),
        Arguments.of(
            "B1 bot beside a person, Master unasked", true, false, bot + person, true, false),
        Arguments.of(
            "C1 code reviewer's blocks alone are nothing to ship",
            true,
            false,
            reviewer,
            false,
            false),
        Arguments.of(
            "C1 code reviewer's blocks hold no ship of the code",
            true,
            false,
            reviewer,
            true,
            true));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cells")
  void isDueAsTheMatrixCellSays(
      String cell,
      boolean postReplies,
      boolean repliesToPeople,
      String written,
      boolean newWork,
      boolean expected,
      @TempDir Path worktree)
      throws IOException {
    Files.writeString(worktree.resolve("review_replies.md"), written);
    ConfigService configService = mock(ConfigService.class);
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(
                    new ConfigFile.CodeReviewConfig(
                        null, postReplies, null, repliesToPeople, null)));
    ReviewDrafts real = new ReviewDrafts(configService);
    TaskState task =
        TaskState.builder("demo", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    when(changes.anythingNewToShip(task)).thenReturn(newWork);
    when(stops.mayShip("ABC-42")).thenReturn(true);

    boolean due =
        new ShipDue(changes, real, stops, new ShipContent(changes, real)).due("ABC-42", task);

    assertThat(due).as(cell).isEqualTo(expected);
  }
}
