package dev.jagt.orchestrator.command.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.stop.BoardStop;
import org.junit.jupiter.api.Test;

class UnstopCommandTest {

  private final BoardStop board = mock(BoardStop.class);

  @Test
  void saysJobsAndIntakeRunAgainOnceTheStopWasLifted() {
    when(board.lift()).thenReturn(true);

    assertThat(new UnstopCommand(board).run(""))
        .isEqualTo("stop lifted: jobs and intake run again");
  }

  @Test
  void saysTheBoardWasNotStoppedWhereThereWasNothingToLift() {
    when(board.lift()).thenReturn(false);

    assertThat(new UnstopCommand(board).run("")).isEqualTo("the board was not stopped");
  }

  @Test
  void isTheHumansAloneSinceTheStopHaltsTheMasterToo() {
    assertThat(new UnstopCommand(board).whyNotTheArbiter()).isPresent();
  }
}
