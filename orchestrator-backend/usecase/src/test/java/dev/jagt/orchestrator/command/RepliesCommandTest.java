package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RepliesCommandTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isOfferedOnACardExactlyWhenItsRoundDraftedReplies(boolean drafted) {
        TaskView card = TaskView.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build(),
                drafted, AutoReviewWatch.none(), Map.of());

        assertThat(new RepliesCommand(mock(ReviewRepliesReport.class)).offeredOn(card)).isEqualTo(drafted);
    }
}
