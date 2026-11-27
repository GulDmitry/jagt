package dev.jagt.orchestrator.service.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.service.role.RoleTurnEndNote;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TurnEndVerdictsTest {

  private static final long NOW = 10_000_000_000L;

  private final ConfigService config = mock(ConfigService.class);
  private final TurnTree turnTree = mock(TurnTree.class);
  private final List<RoleTurnEndNote> noRoleNotes = List.of();
  private final NotesDue notes =
      new NotesDue(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

  @Test
  void sendsOnTurnThatEndsWithOnlyReadCallSinceItBegan() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(3_000L)
            .session(Session.defaults().withWorkStartedAt(2_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered)
        .isInstanceOfSatisfying(
            TurnEndVerdict.Refuse.class,
            refusal -> assertThat(refusal.reason()).contains("update_agent_status"));
  }

  @Test
  void letsTurnEndWhoseStartIsUnknown() {
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }

  @Test
  void sendsOnTurnThatEndsOnQuestionTheHumanAnsweredToStartIt() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .message("keep v2?")
            .report(AgentReport.QUESTION)
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(0L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered)
        .isInstanceOfSatisfying(
            TurnEndVerdict.Refuse.class,
            refusal -> assertThat(refusal.reason()).contains("update_agent_status"));
  }

  @Test
  void sendsOnTurnWhoseTextHoldsOpenQuestionsThatNoReportAsked() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of(
                "ABC-1",
                task,
                new TurnEnding(false, false, "Done so far.\nOPEN QUESTIONS: keep v2?"));

    assertThat(answered)
        .isInstanceOfSatisfying(
            TurnEndVerdict.Refuse.class,
            refusal -> assertThat(refusal.reason()).contains("outcome=question"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "OPEN QUESTIONS: none|false|PLAIN",
        "open questions:   None.|false|PLAIN",
        "no questions at all|false|PLAIN",
        "OPEN QUESTIONS: keep v2?|true|PLAIN",
        "OPEN QUESTIONS: keep v2?|false|QUESTION"
      })
  void letsTurnEndWhoseTextAsksNothingWasSentBackOnceOrWasReportedAsked(
      String text, boolean sentOn, AgentReport report) {
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .message("keep v2?")
            .report(report)
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(sentOn, false, text));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }

  @ParameterizedTest
  @ValueSource(longs = {2_000L, 3_000L})
  void letsTurnEndThatReportedSinceItBegan(long reportedAt) {
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(reportedAt))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void tellsTheHumanWhatTheRoleNotesWhetherOrNotTheTurnWasSentOn(boolean sentOn) {
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, List.of(given -> Optional.of("with the role")))
            .of("ABC-1", task, new TurnEnding(sentOn, false, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Tell("with the role"));
  }

  @Test
  void tellsTheHumanWhatTheRoleNotesWhileTheTurnWaitsOnBackgroundWork() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, List.of(given -> Optional.of("with the role")))
            .of("ABC-1", task, new TurnEnding(false, true, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Tell("with the role"));
  }

  @Test
  void saysNothingAtHandedBackRoundNoRoleNotes() {
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, List.of(given -> Optional.empty()))
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }

  @ParameterizedTest
  @CsvSource({"true, false", "false, true"})
  void letsTurnEndThatWasAlreadySentOnOrWaitsOnBackgroundWork(boolean sentOn, boolean paused) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(0L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(sentOn, paused, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }

  @Test
  void sendsOnTurnEndOnceWhenTheNotesHaveFallenBehindTheWork(@TempDir Path worktree)
      throws Exception {
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("task_notes.md"), "old"),
        FileTime.fromMillis(NOW - 3_600_000L));
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered)
        .isInstanceOfSatisfying(
            TurnEndVerdict.Refuse.class,
            refusal -> assertThat(refusal.reason()).contains("rewrite task_notes.md"));
  }

  @Test
  void neverAsksForTheNotesAgainOnTurnEndTheFirstRefusalSentOn(@TempDir Path worktree)
      throws Exception {
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("task_notes.md"), "old"),
        FileTime.fromMillis(NOW - 3_600_000L));
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, noRoleNotes)
            .of("ABC-1", task, new TurnEnding(true, false, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }
}
