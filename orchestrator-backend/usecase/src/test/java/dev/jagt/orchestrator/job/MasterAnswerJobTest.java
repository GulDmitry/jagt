package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                .message("keep v2?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean())).thenReturn(Optional.of(new MasterPanel.Decision("keep v2 beside v3", "")));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(verdicts).answered("ABC-1", asking, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""), false);
    }

    @Test
    void setsNothingOnATaskWhoseSessionWentBackToWorkWhileTheMasterRead(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("keep v2?").report(AgentReport.QUESTION).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean())).thenAnswer(read -> {
            state.updateTask("ABC-1", task -> task.withReport(AgentReport.PLAIN)
                    .withTurn(task.turnOrNone().closed(Turn.Holder.SESSION, 2_000)));
            return Optional.of(new MasterPanel.Decision("keep v2 beside v3", ""));
        });

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(verdicts, never()).answered(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void leavesAQuestionForAPermissionToTheHumanWhereTheMasterActs(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .message("edits in the deploy worktree denied").report(AgentReport.PERMISSION).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ship", "plan"})
    void leavesAQuestionOnAPlanToThePlanReviewWhateverTheMasterKeeps(String kept, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
                .message("plan revised, approve?").report(AgentReport.QUESTION).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(kept), null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void leavesTheQuestionToTheHumanWhoKeptAnswering(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("keep v2?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null,
                        List.of("answer"), null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @ParameterizedTest
    @EnumSource(TaskStop.Trigger.class)
    void leavesTheQuestionOfATaskJagtStoppedToTheHuman(TaskStop.Trigger trigger, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("keep v2?").report(AgentReport.QUESTION)
                .stop(new TaskStop(trigger, 1L, "the evidence")).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void changesTheApproachOnceThreeAnswersChangedNothingInTheTree(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("override the gate?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(verdicts.answersOverThisTree(asking)).thenReturn(3);

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel).answer("ABC-1", asking, "override the gate?",
                config.load().master(), true);
    }

    @Test
    void stopsARunawayLoopAtTheNinthAnswerOverAnUnchangedTree(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("override the gate?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(verdicts.answersOverThisTree(asking)).thenReturn(9);

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
        assertThat(state.task("ABC-1").orElseThrow().turnOrNone().marked(TaskTurns.UNANSWERED, TaskTurns.asked(asking)))
                .isTrue();
    }

    @Test
    void answersNothingForASessionThatIsWorkingAndAsksNothing(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).message("working").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void asksThePanelOnceAboutAQuestionItsAnswerCouldNotBeActedOn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("keep v2?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean())).thenReturn(Optional.empty());
        MasterAnswerJob job = new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC()));

        job.run();
        job.run();

        verify(panel, times(1)).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void asksThePanelOnceAboutAQuestionWhoseAnswerTheSessionRefused(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("keep v2?").report(AgentReport.QUESTION).build();
        state.putTask("ABC-1", asking);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(panel.answer(eq("ABC-1"), any(), anyString(), any(), anyBoolean())).thenReturn(Optional.of(new MasterPanel.Decision("keep v2", "")));
        when(verdicts.answered(anyString(), any(), anyString(), any(), anyBoolean())).thenReturn(false);
        MasterAnswerJob job = new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC()));

        job.run();
        job.run();

        verify(panel, times(1)).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }

    @Test
    void answersNoRepositoryRequestInProseWhereTheMasterHoldsTheRight(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
        assertThat(state.task("ABC-1").orElseThrow().turnOrNone()
                .marked(TaskTurns.UNANSWERED, TaskTurns.asked(state.task("ABC-1").orElseThrow()))).isTrue();
    }

    @Test
    void leavesARepositoryRequestToTheHumanWhoKeptTheRight(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("repo"), null)));

        new MasterAnswerJob(state, config, panel, verdicts, new TaskTurns(state, Clock.systemUTC())).run();

        verify(panel, never()).answer(anyString(), any(), anyString(), any(), anyBoolean());
    }
}
