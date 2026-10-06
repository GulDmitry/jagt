package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterAnswerJobTest {

    private final StateService state = mock(StateService.class);
    private final ConfigService config = mock(ConfigService.class);
    private final MasterPanel panel = mock(MasterPanel.class);
    private final MasterVerdicts verdicts = mock(MasterVerdicts.class);
    private final MasterAnswerJob job = new MasterAnswerJob(state, config, panel, verdicts);

    @Test
    void answersASessionThatStoppedToAskWhereTheMasterStandsInForTheHuman() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("outcome=question — keep v2?").build();
        when(state.tasks()).thenReturn(Map.of("ABC-1", asking));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(panel.answer(eq("ABC-1"), any(), anyString(), any())).thenReturn(Optional.of("keep v2 beside v3"));

        job.run();

        verify(verdicts).answered("ABC-1", asking, "outcome=question — keep v2?", "keep v2 beside v3");
    }

    @Test
    void leavesTheQuestionToTheHumanWhoKeptAnswering() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("outcome=question — keep v2?").build();
        when(state.tasks()).thenReturn(Map.of("ABC-1", asking));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null,
                        List.of("answer"), null)));

        job.run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any());
    }

    @Test
    void leavesAQuestionToTheHumanWhenItCameBackOverTheTreeTheLastAnswerWasGivenOn() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("outcome=question — reconnect the tool?").build();
        when(state.tasks()).thenReturn(Map.of("ABC-1", asking));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(verdicts.answeredOverThisTree(asking)).thenReturn(true);

        job.run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any());
    }
}
