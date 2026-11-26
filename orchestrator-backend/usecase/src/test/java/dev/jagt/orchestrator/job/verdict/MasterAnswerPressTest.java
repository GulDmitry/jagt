package dev.jagt.orchestrator.job.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.job.master.MasterShip;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MasterAnswerPressTest {

  private final MasterShip ship = mock(MasterShip.class);
  private final MasterAnswerPress answers = new MasterAnswerPress(ship);
  private final TaskState task =
      TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
  private final ConfigFile.ArbiterConfig acting =
      new ConfigFile.ArbiterConfig("act", null, null, null, null);

  @Test
  void shipsWhereTheAnswerIsShipTheMasterHoldsAndTheTreeStandsAsRead() {
    when(ship.standing(task)).thenReturn(Optional.of("tree"));
    when(ship.press("ABC-42", task, TaskAction.SHIP)).thenReturn(true);

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, new Press.Move("ship"), Optional.of("tree"), acting);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.PRESSED);
  }

  @Test
  void deploysWhereTheAnswerIsDeployTheMasterHolds() {
    ConfigFile.ArbiterConfig holdingDeploy =
        new ConfigFile.ArbiterConfig("act", null, null, List.of(), null);
    when(ship.standing(task)).thenReturn(Optional.of("tree"));
    when(ship.press("ABC-42", task, TaskAction.DEPLOY)).thenReturn(true);

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, new Press.Move("deploy"), Optional.of("tree"), holdingDeploy);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.PRESSED);
  }

  @Test
  void reportsRefusedWhereTheMoveTheMasterHoldsWasRefused() {
    when(ship.standing(task)).thenReturn(Optional.of("tree"));
    when(ship.press("ABC-42", task, TaskAction.SHIP)).thenReturn(false);

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, new Press.Move("ship"), Optional.of("tree"), acting);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.REFUSED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"deploy", "lift", "keep v2", ""})
  void relaysWordsWhereTheAnswerIsNoMoveTheMasterHolds(String move) {
    when(ship.standing(task)).thenReturn(Optional.of("tree"));

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, Press.ofId(move), Optional.of("tree"), acting);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.WORDS);
    verify(ship, never()).press(any(), any(), any());
  }

  @Test
  void relaysWordsWhereTheHumanKeepsShip() {
    ConfigFile.ArbiterConfig keepingShip =
        new ConfigFile.ArbiterConfig("act", null, null, List.of("ship"), null);
    when(ship.standing(task)).thenReturn(Optional.of("tree"));

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, new Press.Move("ship"), Optional.of("tree"), keepingShip);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.WORDS);
    verify(ship, never()).press(any(), any(), any());
  }

  @Test
  void pressesNothingWhereTheTreeOrTheDraftsChangedAfterTheMasterReadThem() {
    when(ship.standing(task)).thenReturn(Optional.of("tree and new drafts"));

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, new Press.Move("ship"), Optional.of("tree"), acting);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.WORDS);
    verify(ship, never()).press(any(), any(), any());
  }

  @Test
  void pressesNothingWhereGitCouldNotReadTheTreeForTheAnswer() {
    when(ship.standing(task)).thenReturn(Optional.empty());

    MasterAnswerPress.Result result =
        answers.press("ABC-42", task, new Press.Move("ship"), Optional.empty(), acting);

    assertThat(result).isEqualTo(MasterAnswerPress.Result.WORDS);
    verify(ship, never()).press(any(), any(), any());
  }
}
