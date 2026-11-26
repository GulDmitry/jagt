package dev.jagt.orchestrator.flow.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class SessionStandingTest {

  @Test
  void staysTheSameWhereOnlyPollWasWritten() {
    TaskState read = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    TaskState polled = read.withLastPolledAt(5_000);

    assertThat(SessionStanding.of(polled)).isEqualTo(SessionStanding.of(read));
  }

  @Test
  void differsWhereTheTurnWasOpened() {
    TaskState read = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    TaskState relayed =
        read.withTurn(read.work().turnOrNone().relayed("ship", "digest", "", 1_000));

    assertThat(SessionStanding.of(relayed)).isNotEqualTo(SessionStanding.of(read));
  }
}
