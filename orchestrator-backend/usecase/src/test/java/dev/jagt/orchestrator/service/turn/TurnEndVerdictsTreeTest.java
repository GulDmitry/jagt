package dev.jagt.orchestrator.service.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnEndVerdictsTreeTest {

  private final TurnTree turnTree = mock(TurnTree.class);
  private final NotesDue notes =
      new NotesDue(Clock.fixed(Instant.ofEpochMilli(10_000_000_000L), ZoneOffset.UTC));
  private final TaskState task =
      TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
          .alias("a1")
          .session(Session.defaults().withWorkStartedAt(2_000L))
          .build();

  @Test
  void turnThatChangedTheTreeAtDeployedAndReportedNothingIsRefusedOnce() {
    when(turnTree.changedSinceLastEnd("ABC-1", task)).thenReturn(true);

    TurnEndVerdict answered =
        new TurnEndVerdicts(mock(ConfigService.class), notes, turnTree, List.of())
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered)
        .isInstanceOfSatisfying(
            TurnEndVerdict.Refuse.class,
            refusal -> assertThat(refusal.reason()).contains("the tree changed"));
  }

  @Test
  void reportedTurnEndRecordsTheTreeTheNextTurnIsMeasuredFrom() {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState reported =
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .alias("a1")
            .session(Session.defaults().withWorkStartedAt(2_000L).withReportedAt(3_000L))
            .build();

    new TurnEndVerdicts(config, notes, turnTree, List.of())
        .of("ABC-1", reported, new TurnEnding(false, false, null));

    verify(turnTree).changedSinceLastEnd("ABC-1", reported);
  }

  @Test
  void turnEndTheRefusalSentOnRecordsTheTreeTheNextTurnIsMeasuredFrom() {
    new TurnEndVerdicts(mock(ConfigService.class), notes, turnTree, List.of())
        .of("ABC-1", task, new TurnEnding(true, false, null));

    verify(turnTree).changedSinceLastEnd("ABC-1", task);
  }

  @Test
  void deployCheckThatChangedNothingEndsItsTurn() {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    when(turnTree.changedSinceLastEnd("ABC-1", task)).thenReturn(false);

    TurnEndVerdict answered =
        new TurnEndVerdicts(config, notes, turnTree, List.of())
            .of("ABC-1", task, new TurnEnding(false, false, null));

    assertThat(answered).isEqualTo(new TurnEndVerdict.Silent());
  }
}
