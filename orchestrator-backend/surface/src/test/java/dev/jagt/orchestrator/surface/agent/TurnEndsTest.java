package dev.jagt.orchestrator.surface.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.service.role.RoleTurnEndNote;
import dev.jagt.orchestrator.service.turn.TurnTree;
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

class TurnEndsTest {

  private static final long NOW = 10_000_000_000L;

  private final AgentRuntime runtime = mock(AgentRuntime.class);
  private final ConfigService config = mock(ConfigService.class);
  private final TurnTree turnTree = mock(TurnTree.class);
  private final List<RoleTurnEndNote> noRoleNotes = List.of();
  private final NotesDue notes =
      new NotesDue(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

  @Test
  void sendsOnTurnThatEndsWithOnlyReadCallSinceItBegan() {
    when(runtime.refusedTurnEnd(contains("update_agent_status")))
        .thenReturn("{\"decision\": \"block\"}");
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(3_000L)
            .session(Session.defaults().withWorkStartedAt(2_000L))
            .build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, false, false);

    assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
  }

  @Test
  void letsTurnEndWhoseStartIsUnknown() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, false, false);

    assertThat(answered).isEmpty();
  }

  @Test
  void sendsOnTurnThatEndsOnQuestionTheHumanAnsweredToStartIt() {
    when(runtime.refusedTurnEnd(contains("update_agent_status")))
        .thenReturn("{\"decision\": \"block\"}");
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .message("keep v2?")
            .report(AgentReport.QUESTION)
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(0L))
            .build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, false, false);

    assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
  }

  @ParameterizedTest
  @ValueSource(longs = {2_000L, 3_000L})
  void letsTurnEndThatReportedSinceItBegan(long reportedAt) {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(reportedAt))
            .build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, false, false);

    assertThat(answered).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void tellsTheHumanWhatTheRoleNotesWhetherOrNotTheTurnWasSentOn(boolean sentOn) {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(runtime.toldTheHuman("with the role"))
        .thenReturn("{\"systemMessage\": \"with the role\"}");
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    String answered =
        new TurnEnds(
                runtime, config, notes, turnTree, List.of(given -> Optional.of("with the role")))
            .answer("ABC-1", task, sentOn, false);

    assertThat(answered).isEqualTo("{\"systemMessage\": \"with the role\"}");
  }

  @Test
  void tellsTheHumanWhatTheRoleNotesWhileTheTurnWaitsOnBackgroundWork() {
    when(runtime.toldTheHuman("with the role"))
        .thenReturn("{\"systemMessage\": \"with the role\"}");
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build();

    String answered =
        new TurnEnds(
                runtime, config, notes, turnTree, List.of(given -> Optional.of("with the role")))
            .answer("ABC-1", task, false, true);

    assertThat(answered).isEqualTo("{\"systemMessage\": \"with the role\"}");
  }

  @Test
  void saysNothingAtHandedBackRoundNoRoleNotes() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, List.of(given -> Optional.empty()))
            .answer("ABC-1", task, false, false);

    assertThat(answered).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"true, false", "false, true"})
  void letsTurnEndThatWasAlreadySentOnOrWaitsOnBackgroundWork(boolean sentOn, boolean paused) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(0L))
            .build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, sentOn, paused);

    assertThat(answered).isEmpty();
  }

  @Test
  void sendsOnTurnEndOnceWhenTheNotesHaveFallenBehindTheWork(@TempDir Path worktree)
      throws Exception {
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("task_notes.md"), "old"),
        FileTime.fromMillis(NOW - 3_600_000L));
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(runtime.refusedTurnEnd(contains("rewrite task_notes.md")))
        .thenReturn("{\"decision\": \"block\"}");
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, false, false);

    assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
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

    String answered =
        new TurnEnds(runtime, config, notes, turnTree, noRoleNotes)
            .answer("ABC-1", task, true, false);

    assertThat(answered).isEmpty();
  }
}
