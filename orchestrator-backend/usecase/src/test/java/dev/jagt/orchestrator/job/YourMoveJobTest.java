package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class YourMoveJobTest {

    private final Notifications notifications = mock(Notifications.class);
    private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final SteppingClock clock = new SteppingClock();

    private static final class SteppingClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void tapsYouWhenASessionHandsARoundBackAndNoMasterReadsIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId()) && "review_pending".equals(sent.title())));
    }

    @Test
    void tapsYouWhenASessionStopsToAskWithoutLeavingTheStatusItWorkedIn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("step 2").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("awaiting: which uniqueness rule").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verify(notifications).send(argThat(sent -> "needs input".equals(sent.title())));
    }

    @Test
    void tapsYouOnceForAMoveSeenOnManyTicks(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();
        job.run();
        job.run();

        verify(notifications, times(1)).send(any());
    }

    @Test
    void staysQuietOnAKeepAliveWhileTheSessionWorks(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("step 1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("step 2").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void staysQuietAboutATaskAlreadyYoursWhenJagtStartsUp(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void staysQuietAboutATaskThatLeftAndCameBackAlreadyYours(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        job.run();
        state.removeTask("ABC-1");
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void staysQuietWhileTheMasterStillReadsTheHandBack(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void tapsYouWhenTheReviewApprovesTheRound(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .alias("a1").mrUrl("http://mr/1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId()) && sent.body().contains("approved")));
    }

    @Test
    void tapsYouWhenTheChecksFailedAndTheRoundIsYoursToRelay(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .alias("a1").mrUrl("http://mr/1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verify(notifications).send(argThat(sent -> "ci_failed".equals(sent.title())));
    }

    @Test
    void tellsYouAboutTheDraftedRepliesWaitingInTheWorktree(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        Files.createDirectories(root.resolve("wt"));
        Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");
        String worktree = root.resolve("wt").toString();
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.IN_PROGRESS).alias("a1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.REVIEW_PENDING).alias("a1")
                .message("widget fixed").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).send(sent.capture());
        assertThat(sent.getValue().body()).containsOnlyOnce("drafted replies").contains("review_replies.md");
    }

    @Test
    void tapsYouOnceTheMastersReadyVerdictLeavesThePlanToYouWhenYouKeptApprovingPlans(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        Files.createDirectories(root.resolve("wt"));
        String worktree = root.resolve("wt").toString();
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.PLAN_PENDING).alias("a1").build());
        job.run();
        Files.writeString(root.resolve("wt").resolve(MasterReview.FILE), "VERDICT: ready\n");

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();
        job.run();

        verify(notifications, times(1)).send(argThat(sent -> "ABC-1".equals(sent.taskId())
                && "plan_pending".equals(sent.title())));
    }

    @Test
    void staysQuietWhileTheMasterReadsAPlan(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        Files.createDirectories(root.resolve("wt"));
        String worktree = root.resolve("wt").toString();
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.IN_PROGRESS).alias("a1").build());
        job.run();
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.PLAN_PENDING).alias("a1").build());

        job.run();
        clock.advance(Duration.ofSeconds(10));
        job.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void neverTapsYouForARoundTheMasterShipsBeforeTheMoveSettles(@TempDir Path root) throws IOException {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(
                new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null)));
        YourMoveJob job = new YourMoveJob(state, notifications,
                new HandBack(worktreeChanges, new Rounds(configService, new MasterReview()), configService), clock);
        Files.createDirectories(root.resolve("wt"));
        String worktree = root.resolve("wt").toString();
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.REVIEW_PENDING).alias("a1").build());
        job.run();
        Files.writeString(root.resolve("wt").resolve(MasterReview.FILE), "VERDICT: ready\n");
        job.run();
        clock.advance(Duration.ofSeconds(1));
        state.putTask("ABC-1", TaskState.builder("proj", worktree, TaskStatus.SHIPPING).alias("a1").build());
        job.run();

        clock.advance(Duration.ofSeconds(10));
        job.run();

        verifyNoInteractions(notifications);
    }
}
