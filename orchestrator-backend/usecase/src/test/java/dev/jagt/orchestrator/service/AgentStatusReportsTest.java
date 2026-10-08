package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentStatusReportsTest {

    private final Notifications notifications = mock(Notifications.class);
    private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
    private final ConfigService configService = mock(ConfigService.class);

    @Test
    void tellsTheSessionItsHandBackIsWaitingOnVerificationRatherThanOnTheHuman(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.project("proj")).thenReturn(new dev.jagt.orchestrator.task.ProjectConfig(
                "/repo", "origin/main", "dev", List.of(), List.of("./gradlew", "test")));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

        assertThat(answer).contains("VERIFYING");
    }

    @Test
    void answersAReportOnAVanishedTaskAsAFactNotAFieldToFix(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        assertThatThrownBy(() -> reports.report(TaskStatus.IN_PROGRESS, "working", "ABC-9"))
                .isInstanceOfSatisfying(Refusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.NO_SUCH_TASK));
    }

    @Test
    void tellsTheSessionWhereItsReportLandedInTheStateItWasWrittenTo(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVERTED).alias("a1").build());
        StateService staleView = spy(state);
        doReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()))
                .when(staleView).task("ABC-1");
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(staleView, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

        assertThat(answer).startsWith("Task ABC-1 stays REVERTED");
        verify(notifications, never()).send(any());
    }

    @Test
    void tellsTheSessionTheMasterReadsItsHandBackNext(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));

        String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

        assertThat(answer).endsWith("the Master reads this round next; end your turn");
    }

    @Test
    void storesTheRequestLinkTheAgentPutInItsStatusMessage(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: https://gitlab/x/-/merge_requests/9", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void leavesADeployedTaskAloneWhenAPolledRoundKeepsCallingItReviewed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .message("deployed").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.markRead("ABC-1", TaskStatus.REVIEWED);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
        assertThat(state.task("ABC-1").orElseThrow().message()).isEqualTo("deployed");
    }

    @Test
    void takesTheRequestLinkFromTheArgumentRatherThanFromTheProse(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(new AgentStatusMessage("CI_POLLING", "handed over", null, "https://gitlab/x/-/merge_requests/9", Map.of()), "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void linksEveryRepositoryToItsOwnRequestAndRecordsOneRound(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .repos(List.of(new TaskRepo("proj", "/wt", "git@host:proj.git", null, null),
                        new TaskRepo("web", "/wt-web", "git@host:web.git", null, null))).build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(new AgentStatusMessage("CI_POLLING", "requests up", null, null, Map.of("proj", "https://host/proj/-/merge_requests/9",
                        "web", "https://host/web/-/merge_requests/3")), "ABC-1");

        TaskState reported = state.task("ABC-1").orElseThrow();
        assertThat(reported.repos()).extracting(TaskRepo::mrUrl)
                .containsExactly("https://host/proj/-/merge_requests/9", "https://host/web/-/merge_requests/3");
        assertThat(reported.history()).extracting(StatusChange::status)
                .containsOnlyOnce(TaskStatus.CI_POLLING);
    }

    @Test
    void refusesARequestReportedUnderAProjectTheTaskDoesNotHold(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        assertThatThrownBy(() -> reports.report(new AgentStatusMessage("CI_POLLING", "requests up", null, null, Map.of("frontend", "https://host/x/-/merge_requests/9")), "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project 'frontend' is not on this task")
                .hasMessageContaining("proj");
    }

    @Test
    void armsAFreshRoundWhenASecondRepositoryFinallyOpensItsRequest(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .repos(List.of(new TaskRepo("proj", "/wt", "git@host:proj.git", "https://host/proj/mr/9", null),
                        new TaskRepo("web", "/wt-web", "git@host:web.git", null, null)))
                .mrCreatedAt(1_000L).lastPolledAt(9_000L).build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(new AgentStatusMessage("CI_POLLING", "web is up too", null, null, Map.of("proj", "https://host/proj/mr/9", "web", "https://host/web/mr/3")), "ABC-1");

        TaskState reported = state.task("ABC-1").orElseThrow();
        assertThat(reported.reviewRequestOf("web")).contains("https://host/web/mr/3");
        assertThat(reported.mrCreatedAt()).isGreaterThan(1_000L);
        assertThat(reported.lastPolledAt()).isZero();
    }

    @Test
    void readsTheOutcomeFromTheArgumentWhenTheMessageCarriesNoMarker(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(new AgentStatusMessage("IN_PROGRESS", "which cache should this use", "question", null, Map.of()), "ABC-1");

        assertThat(AgentReport.of(state.task("ABC-1").orElseThrow().message()))
                .isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void recordsARoundWithADiffWhenTheWorktreeContradictsANoChangesClaim(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/mr/1").build());
        when(worktreeChanges.anyUncommitted(any())).thenReturn(true);
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(new AgentStatusMessage("REVIEW_PENDING", "already handled", "no_changes", null, Map.of()), "ABC-1");

        assertThat(AgentReport.of(state.task("ABC-1").orElseThrow().message())).isEqualTo(AgentReport.PLAIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no_changes", "no-changes", "no changes"})
    void keepsANoChangesRoundThatTheWorktreeBearsOut(String outcome, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(new AgentStatusMessage("REVIEW_PENDING", "already handled", outcome, null, Map.of()), "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().message()).isEqualTo("no changes: already handled");
    }

    @Test
    void recordsTheOutcomeAnAgentTypedIntoTheMessageInsteadOfTheField(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.REVIEW_PENDING, "outcome=no_changes: withdrawn thread relayed again", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().message())
                .isEqualTo("no changes: withdrawn thread relayed again");
    }

    @Test
    void notifiesHumanWhenAgentFinishesAndHandsBackForReview(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

        reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())));
    }

    @Test
    void leavesTheHumanUntappedWhileTheMasterReadsTheHandBackFirst(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

        verify(notifications, never()).send(any());
    }

    @Test
    void doesNotNotifyOnRoutineInProgressKeepAlive(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.IN_PROGRESS, "step 2", "ABC-1");

        verifyNoInteractions(notifications);
    }

    @Test
    void notifiesHumanWhenAgentStopsToAskWithoutLeavingTheStatusItWasWorkingIn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("step 2").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.IN_PROGRESS, "awaiting: which uniqueness rule", "ABC-1");

        verify(notifications).send(argThat(sent -> "needs input".equals(sent.title())));
    }

    @Test
    void doesNotNotifyAgainWhileTheAgentRepeatsTheQuestionItIsStillWaitingOn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("awaiting: which uniqueness rule").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.IN_PROGRESS, "awaiting: which uniqueness rule", "ABC-1");

        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"branch pushed", "pushed, see the http docs for the request"})
    void refusesToSayATaskIsWaitingOnChecksWithoutNamingTheRequest(String message, @TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        assertThatThrownBy(() -> reports.report(TaskStatus.CI_POLLING, message, "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required with CI_POLLING");
    }

    @Test
    void letsATaskSayItIsWaitingOnTheChecksOnceItNamesTheRequest(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void refusesTheStatusEvenForATaskThatAlreadyCarriesARequestLink(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1")
                .mrUrl("https://gitlab.example/g/p/-/merge_requests/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        assertThatThrownBy(() -> reports.report(TaskStatus.CI_POLLING, "waiting for the pipeline", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required with CI_POLLING");
    }

    @Test
    void truncatesStatusMessageToOneDashboardLineWhenAgentSendsAnEssay(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.IN_PROGRESS, "root cause\nanalysis ".repeat(20), "ABC-1");

        String stored = state.task("ABC-1").orElseThrow().message();
        assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("…");
    }

    @Test
    void keepsTheWholeRequestLinkWhenTheMessageIsTooLongForOneDashboardLine(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        String link = "https://gitlab.example/group/subgroup/team/project/-/merge_requests/1234567";
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "pipeline queued after the push — MR: " + link, "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo(link);
    }

    @Test
    void advancesToApprovedAndTapsTheHumanTheFirstTime(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.markRead("ABC-1", TaskStatus.APPROVED);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())
                && sent.body().contains("approved")));
    }

    @Test
    void saysNothingWhenARoundCameBackCleanButUnapproved(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.markRead("ABC-1", TaskStatus.REVIEWED);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEWED);
        verify(notifications, never()).send(any());
    }

    @Test
    void staysQuietAboutAnApprovalTheHumanHasAlreadySeen(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .alias("a1").mrUrl("http://mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.markRead("ABC-1", TaskStatus.APPROVED);

        verify(notifications, never()).send(any());
    }

    @Test
    void writesNothingWhenAPollReadsTheOutcomeTheTaskAlreadyHolds(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).alias("a1")
                .mrUrl("http://mr/1").message("awaiting: squash or keep the commits?")
                .lastActiveTimestamp(1_700_000_000_000L).silentSince(1_700_000_000_000L).build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.markRead("ABC-1", TaskStatus.REVIEWED);

        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.message()).isEqualTo("awaiting: squash or keep the commits?");
        assertThat(after.lastActiveTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(after.silentSince()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void stampsThePollingWindowWhenARequestIsFirstLinked(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isPositive();
    }

    @Test
    void startsAFreshPollingWindowForEachRoundHandedBackOnTheSameRequest(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        long lastRound = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .alias("a1").mrUrl("http://mr/1").mrCreatedAt(lastRound).lastPolledAt(lastRound).build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isGreaterThan(lastRound);
    }

    @Test
    void startsAFreshWindowWhenTheAgentNamesAnotherRequestWithoutLeavingCiPolling(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        long lastRound = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").mrCreatedAt(lastRound).build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: http://mr/2", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isGreaterThan(lastRound);
    }

    @Test
    void dropsThePreviousRoundsChecksVerdictWhenARoundGoesBackOutForReview(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .alias("a1").mrUrl("http://mr/1").mrCreatedAt(12345L).pipelineStatus("failed").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().pipelineStatus()).isNull();
    }

    @Test
    void keepsThePollingWindowWhileTheAgentRepeatsThatItIsWaitingOnTheSameChecks(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").mrCreatedAt(12345L).build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.report(TaskStatus.CI_POLLING, "MR: http://mr/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isEqualTo(12345L);
    }

    @Test
    void tellsTheHumanAboutTheDraftedRepliesWaitingInTheWorktree(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        Files.createDirectories(root.resolve("wt"));
        Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");
        state.putTask("ABC-1", TaskState.builder("proj", root.resolve("wt").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

        reports.report(TaskStatus.REVIEW_PENDING, "widget fixed", "ABC-1");

        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())
                && sent.body().contains("review_replies.md")));
    }

    @Test
    void doesNotRepeatTheDraftedRepliesWhenTheRoundChangedNothingAndTheAdviceAlreadySaysIt(@TempDir Path root)
            throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        Files.createDirectories(root.resolve("wt"));
        Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: already handled\n");
        state.putTask("ABC-1", TaskState.builder("proj", root.resolve("wt").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").mrUrl("https://host/mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

        reports.report(TaskStatus.REVIEW_PENDING, "no changes: every comment already handled", "ABC-1");

        ArgumentCaptor<Notification> ping = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).send(ping.capture());
        assertThat(ping.getValue().body()).containsOnlyOnce("drafted replies");
    }

    @Test
    void refusesToPullATaskTheReviewHasPassedBackIntoWaitingOnChecks(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.APPROVED)
                .alias("a1").mrUrl("https://host/mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        assertThatThrownBy(() -> reports.report(TaskStatus.CI_POLLING, "review request: https://host/mr/1", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already APPROVED");
        assertThat(state.task("ABC-1")).get().extracting(TaskState::status).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void putsARedRoundOnTheBoardWithoutTappingTheHumanTwice(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));

        reports.markRead("ABC-1", TaskStatus.CI_FAILED);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_FAILED);
        verify(notifications, never()).send(any());
    }

    @Test
    void tellsTheSessionItsHandBackIsVerifiedBeforeTheMasterReadsIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state),
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(configService.project("proj")).thenReturn(new dev.jagt.orchestrator.task.ProjectConfig(
                "/repo", "origin/main", "dev", List.of(), List.of("./gradlew", "test")));

        String answer = reports.report(TaskStatus.REVIEW_PENDING, "done", "ABC-1");

        assertThat(answer).endsWith("verification runs first, and the Master reads this round once it passes;"
                + " end your turn");
    }
}
