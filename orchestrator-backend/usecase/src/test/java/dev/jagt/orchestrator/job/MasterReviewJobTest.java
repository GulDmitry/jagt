package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterReviewJobTest {

    private final ConfigService config = mock(ConfigService.class);
    private final MasterPanel panel = mock(MasterPanel.class);
    private final MasterVerdicts verdicts = mock(MasterVerdicts.class);

    private StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    private ConfigService.ConfigFile.MasterConfig master(String mode) {
        ConfigService.ConfigFile.MasterConfig master =
                new ConfigService.ConfigFile.MasterConfig(mode, null, null, null, null);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(master));
        return master;
    }

    @Test
    void readsNoRoundWhileTheMasterIsOff(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        master("off");

        new MasterReviewJob(state, config, panel, new MasterReview(), verdicts).run();

        verify(panel, never()).review(anyString(), any(), any());
    }

    @Test
    void handsARoundThatCameBackAndNobodyJudgedToTheMastersPanel(@TempDir Path root) {
        StateService state = stateIn(root);
        TaskState back = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
                .lastActiveTimestamp(2_000L).build();
        state.putTask("ABC-1", back);
        ConfigService.ConfigFile.MasterConfig master = master("judge");

        new MasterReviewJob(state, config, panel, new MasterReview(), verdicts).run();

        verify(panel).review("ABC-1", back, master);
    }

    @Test
    void handsAPlanToThePlanReadingPanelNotTheRoundReadingOne(@TempDir Path root) {
        StateService state = stateIn(root);
        TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
        state.putTask("ABC-1", planned);
        ConfigService.ConfigFile.MasterConfig master = master("judge");

        new MasterReviewJob(state, config, panel, new MasterReview(), verdicts).run();

        verify(panel).plan("ABC-1", planned, master);
        verify(panel, never()).review(anyString(), any(), any());
    }

    @Test
    void readsNothingOfATaskTheSessionStillWorksOn(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        master("judge");

        new MasterReviewJob(state, config, panel, new MasterReview(), verdicts).run();

        verify(panel, never()).review(anyString(), any(), any());
        verify(panel, never()).plan(anyString(), any(), any());
    }

    @Test
    void actsOnTheVerdictWrittenAboutTheRoundInsteadOfAskingAgain(@TempDir Path root) throws IOException {
        StateService state = stateIn(root);
        TaskState back = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
                .lastActiveTimestamp(2_000L).build();
        state.putTask("ABC-1", back);
        Files.writeString(root.resolve(MasterReview.FILE), "VERDICT: ready\n");
        ConfigService.ConfigFile.MasterConfig master = master("act");

        new MasterReviewJob(state, config, panel, new MasterReview(), verdicts).run();

        verify(verdicts).act(eq("ABC-1"), eq(back),
                any(MasterReview.Verdict.class), eq(master));
        verify(panel, never()).review(anyString(), any(), any());
    }

    @Test
    void asksAgainWhenTheOnlyVerdictPredatesTheHandBack(@TempDir Path root) throws IOException {
        StateService state = stateIn(root);
        TaskState back = TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
                .lastActiveTimestamp(2_000L).build();
        state.putTask("ABC-1", back);
        Path file = root.resolve(MasterReview.FILE);
        Files.writeString(file, "VERDICT: ready\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000L));
        ConfigService.ConfigFile.MasterConfig master = master("act");

        new MasterReviewJob(state, config, panel, new MasterReview(), verdicts).run();

        verify(verdicts, never()).act(anyString(), any(), any(), any());
        verify(panel).review("ABC-1", back, master);
    }

    @Test
    void leavesARoundWhoseQuestionTheMasterAnswersToTheAnswerNotTheReview() {
        TaskState asking = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .message("outcome=question — override the gate?").build();

        boolean reviewable = MasterReviewJob.reviewable(asking, false, true);

        assertThat(reviewable).isFalse();
    }

    @Test
    void handsAPlanNobodyReadYetToTheMaster() {
        TaskState planned = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).message("plan written").build();

        boolean reviewable = MasterReviewJob.reviewable(planned, false, true);

        assertThat(reviewable).isTrue();
    }
}
