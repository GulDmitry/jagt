package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ConflictEndingTest {

  @ParameterizedTest
  @EnumSource(Deployment.Cause.class)
  void everyConflictIsEndedBySomeRead(Deployment.Cause cause) {
    assertThat(ConflictEnding.of(cause)).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = Deployment.Cause.class,
      names = {"MERGE", "REVERT"})
  void aMergeOrRevertConflictIsNeverEndedByALaterGreenPipeline(Deployment.Cause cause) {
    assertThat(ConflictEnding.of(cause)).doesNotContain(ConflictEnding.LATER_GREEN);
  }

  @Test
  void aRevertConflictReadsNoRequest() {
    assertThat(ConflictEnding.of(Deployment.Cause.REVERT))
        .doesNotContain(ConflictEnding.REQUEST_CLOSED);
  }

  @Test
  void aTaskInNoConflictHasNothingToRead() {
    TaskState deployed = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build();

    assertThat(ConflictEnding.of(deployed)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "LATER_GREEN,DEPLOY_UNREAD,true",
    "LATER_GREEN,STALLED,false",
    "REQUEST_CLOSED,STALLED,true",
    "STAGED_FIX,STALLED,true",
    "STAGED_FIX,PAUSE,false"
  })
  void aReadRunsOnlyUnderTheStopsItNames(
      ConflictEnding ending, TaskStop.Trigger trigger, boolean reads) {
    TaskState stopped =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(trigger, 5, "evidence"))
            .build();

    assertThat(ending.readsUnder(stopped)).isEqualTo(reads);
  }
}
