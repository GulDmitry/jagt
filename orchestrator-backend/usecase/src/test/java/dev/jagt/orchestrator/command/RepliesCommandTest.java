package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.view.RoundState;
import dev.jagt.orchestrator.flow.view.TaskView;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.AutoReviewWatch;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepliesCommandTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void isOfferedOnCardExactlyWhenItsRoundDraftedReplies(boolean drafted) {
    TaskView card =
        TaskView.of(
            new FlowRules(new TaskChart(Composition.builtIn())),
            "ABC-1",
            TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build(),
            RoundState.of(AgentReport.PLAIN, drafted),
            AutoReviewWatch.none(),
            Map.of());

    assertThat(new RepliesCommand(mock(ReviewRepliesReport.class)).offeredOn(card))
        .isEqualTo(drafted);
  }

  @Test
  void feedsTheRoundTab() {
    assertThat(new RepliesCommand(mock(ReviewRepliesReport.class)).part()).isEqualTo("round");
  }
}
