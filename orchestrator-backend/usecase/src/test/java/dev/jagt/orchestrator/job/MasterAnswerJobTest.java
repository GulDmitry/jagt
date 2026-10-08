package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterAnswerJobTest {

    private final ConfigService config = mock(ConfigService.class);
    private final MasterPanel panel = mock(MasterPanel.class);
    private final MasterVerdicts verdicts = mock(MasterVerdicts.class);

    @Test
    void answersASessionThatStoppedToAskWhereTheMasterStandsInForTheHuman(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("outcome=question — keep v2?").build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean())).thenReturn(Optional.of("keep v2 beside v3"));

        new MasterAnswerJob(state, config, panel, verdicts).run();

        verify(verdicts).answered("ABC-1", asking, "outcome=question — keep v2?", "keep v2 beside v3", false);
    }

    @Test
    void leavesTheQuestionToTheHumanWhoKeptAnswering(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("outcome=question — keep v2?").build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null,
                        List.of("answer"), null)));

        new MasterAnswerJob(state, config, panel, verdicts).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void changesTheApproachOnceThreeAnswersChangedNothingInTheTree(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("outcome=question — override the gate?").build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(verdicts.answersOverThisTree(asking)).thenReturn(3);

        new MasterAnswerJob(state, config, panel, verdicts).run();

        verify(panel).answer("ABC-1", asking, "outcome=question — override the gate?",
                config.load().master(), true);
    }

    @Test
    void stopsARunawayLoopAtTheNinthAnswerOverAnUnchangedTree(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("outcome=question — override the gate?").build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(verdicts.answersOverThisTree(asking)).thenReturn(9);

        new MasterAnswerJob(state, config, panel, verdicts).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }
}
