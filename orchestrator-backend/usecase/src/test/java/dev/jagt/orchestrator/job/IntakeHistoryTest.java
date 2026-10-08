package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeHistoryTest {

    private final StateService stateService = mock(StateService.class);
    private final FinishedTasks finished = mock(FinishedTasks.class);
    private final Notifications notifications = mock(Notifications.class);
    private final IntakeHistory history = new IntakeHistory(stateService, finished, notifications);

    @Test
    void holdsTheKeysOfTheTasksItRunsAndOfTheOnesItAlreadyFinished() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-2",
                TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a2").build(), 0L)));

        assertThat(history.held()).containsExactlyInAnyOrder("ABC-1", "ABC-2");
    }

    @Test
    void neverReadsAnItemItAlreadyTurnedAway() {
        when(stateService.tasks()).thenReturn(Map.of());
        when(finished.all()).thenReturn(List.of());

        history.turnAway("ABC-42", "nothing routes it");

        assertThat(history.held()).containsExactly("ABC-42");
    }

    @Test
    void tellsAHumanAboutAnItemItTurnedAwayOnceRatherThanEveryPoll() {
        history.turnAway("ABC-42", "nothing routes it");
        history.turnAway("ABC-42", "nothing routes it");

        verify(notifications, times(1)).send(any(Notification.class));
    }
}
