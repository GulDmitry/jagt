package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.job.Jobs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardRendererTest {

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void showsTheTicketAboveTheReviewRequestBecauseThatIsTheOrderAHumanReadsThem(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").mrUrl("https://host/x/-/merge_requests/9")
                .ticketUrl("https://tracker/browse/ABC-1").build());

        String out = rendererFor(state).render();

        assertThat(out).contains("└ https://tracker/browse/ABC-1");
        assertThat(out.indexOf("https://tracker/browse/ABC-1"))
                .isLessThan(out.indexOf("https://host/x/-/merge_requests/9"));
    }

    /**
     * The console prefixes the request with what the read said about it, and both surfaces show the approval from
     * the moment the request opens — a status only ever says so once the approval has already landed.
     */
    @Test
    void printsWhetherTheRequestIsApprovedInFrontOfTheLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").mrUrl("https://host/mr/1").approved(false).build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt2", TaskStatus.REVIEWED)
                .alias("a2").title("title").mrUrl("https://host/mr/2").approved(true).build());

        String out = rendererFor(state).render();

        assertThat(out).contains("└ not approved · https://host/mr/1");
        assertThat(out).contains("└ APPROVED · https://host/mr/2");
    }

    /**
     * Every column is truncated, not merely padded: one value a character too long shifts every column after it,
     * and the shell slices the row at fixed offsets to colour the id and the title.
     */
    @Test
    void keepsTheColumnsWhereTheyAreWhateverTheLongestValueIs(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("a-very-long-project-key", "/wt", TaskStatus.DEPLOY_CONFLICT)
                .alias("a1").title("title").build());

        String out = rendererFor(state).render();

        String header = out.lines().filter(line -> line.startsWith("ALIAS")).findFirst().orElseThrow();
        String row = out.lines().filter(line -> line.startsWith("a1")).findFirst().orElseThrow();
        assertThat(row.indexOf("title")).isEqualTo(header.indexOf("TITLE"));
    }

    @Test
    void pointsAtTheDraftedRepliesWaitingInTheWorktree(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("review_replies.md"), "> rename x\n\nRenamed it.\n");
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1").title("title").build());

        String out = rendererFor(state).render();

        assertThat(out).contains("└ drafted review replies — `replies a1` reads them before you ship");
    }

    @Test
    void saysHowLongTheTaskHasBeenInItsCurrentStatus(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("title")
                .history(List.of(new StatusChange(TaskStatus.REVIEW_PENDING,
                        System.currentTimeMillis() - 7_200_000, null)))
                .build());

        String out = rendererFor(state).render();

        assertThat(out).contains("(not shipped 2h)");
    }

    /** The status clock restarts on every round; how long the review has been waiting is the other clock. */
    @Test
    void saysHowLongTheReviewRequestHasBeenOpenNextToTheStatusClock(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").mrUrl("https://host/x/-/merge_requests/9")
                .requestOpenedAt(System.currentTimeMillis() - Duration.ofHours(8).toMillis())
                .history(List.of(new StatusChange(TaskStatus.CI_POLLING,
                        System.currentTimeMillis() - 600_000, null)))
                .build());

        String out = rendererFor(state).render();

        assertThat(out).contains("(out for review 10m · MR 8h)");
    }

    @Test
    void saysNothingAboutTheRequestsAgeWhileNoReadHasSaidWhenItWasOpened(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").mrUrl("https://host/x/-/merge_requests/9").build());

        String out = rendererFor(state).render();

        assertThat(out).doesNotContain("MR ");
    }

    /** A row that moves on the agent's next keep-alive costs a human the position they had learnt. */
    @Test
    void keepsARowWhereItIsWhateverAgentReportedLast(@TempDir Path root) {
        StateService state = stateIn(root);
        long older = 1_700_000_000_000L;
        long newer = older + 3_600_000;
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(older).alias("a1").title("older task").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(newer).alias("a2").title("newer task").build());

        String out = rendererFor(state).render();

        assertThat(out.indexOf("ABC-1")).isLessThan(out.indexOf("ABC-2"));
        assertThat(out).contains(DashboardRenderer.stamp(older), DashboardRenderer.stamp(newer));
    }

    @Test
    void showsWhatJagtHasSpentOnATaskAndForTheWholeSession(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").build());
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(63_500, 0, 500, 0.12));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(63_500, 0, 500, 0.12));

        String out = rendererFor(state, tracker).render();

        assertThat(out).contains("TOKENS");
        assertThat(out).contains("64k");
        assertThat(out).contains("spend 1 call / 64k tok");
    }

    @Test
    void leavesTheTokenColumnEmptyForATaskThatHasCostNothing(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        String out = rendererFor(state).render();

        assertThat(out).doesNotContain("spend");
        assertThat(out.lines().filter(l -> l.startsWith("a1")).findFirst().orElseThrow())
                .contains(" - ");
    }

    @Test
    void omitsTheTicketLineWhenNoUrlWasRead(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .alias("a1").title("title").build());

        assertThat(rendererFor(state).render()).doesNotContain("└");
    }

    /** The header line is INDENTED: the TUI colours any unindented line as a task row, and this is not one. */
    @Test
    void saysWhenTheUnattendedPollWillNextLookAtATaskOutForReview(@TempDir Path root) {
        StateService state = stateIn(root);
        long shipped = System.currentTimeMillis();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withAutoReview(new ConfigService.ConfigFile.AutoReviewConfig(true, null, null, null)));

        String out = new DashboardRenderer(new TaskViews(state, config), new UsageTracker(state),
                new Jobs(List.of())).render();

        assertThat(out).contains("\n  auto-review on\n");
        assertThat(out).contains("└ auto-review · next poll in 10m");
    }

    @Test
    void saysWhenNothingWillPollATaskAnyMore(@TempDir Path root) {
        StateService state = stateIn(root);
        long shipped = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withAutoReview(new ConfigService.ConfigFile.AutoReviewConfig(true, null, null, null)));

        String out = new DashboardRenderer(new TaskViews(state, config), new UsageTracker(state),
                new Jobs(List.of())).render();

        assertThat(out).contains("└ auto-review · no further polls: this round is past its 24h window");
    }

    @Test
    void namesTheTaskThatNothingPollsWhileTheRestAreWatched(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(System.currentTimeMillis())
                .autoReview(false).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withAutoReview(new ConfigService.ConfigFile.AutoReviewConfig(true, null, null, null)));

        String out = new DashboardRenderer(new TaskViews(state, config), new UsageTracker(state),
                new Jobs(List.of())).render();

        assertThat(out).contains("└ auto-review · polling is disabled for this task");
    }

    /** With polling off, the header has to say so — silence is what a human cannot tell apart from waiting. */
    @Test
    void saysThatNothingPollsAtAllWhenAutoReviewIsSwitchedOff(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1")
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(System.currentTimeMillis()).build());

        String out = rendererFor(state).render();

        assertThat(out).contains("auto-review off");
        assertThat(out).doesNotContain("next poll");
    }

    private static DashboardRenderer rendererFor(StateService state) {
        return rendererFor(state, new UsageTracker(state));
    }

    @Test
    void headerSaysWhenTheNextUnattendedRunIsDue(@TempDir Path root) {
        Jobs jobs = mock(Jobs.class);
        when(jobs.summary(anyLong()))
                .thenReturn(new Jobs.Summary(1, System.currentTimeMillis() + Duration.ofHours(2).toMillis(), 0));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        String out = new DashboardRenderer(new TaskViews(stateIn(root), config),
                new UsageTracker(stateIn(root)), jobs).render();

        assertThat(out).contains("· jobs next in 2h");
    }

    @Test
    void headerSaysAJobFailedInsteadOfWhenItRunsNext(@TempDir Path root) {
        Jobs jobs = mock(Jobs.class);
        when(jobs.summary(anyLong()))
                .thenReturn(new Jobs.Summary(1, System.currentTimeMillis() + 1_000, 1));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        String out = new DashboardRenderer(new TaskViews(stateIn(root), config),
                new UsageTracker(stateIn(root)), jobs).render();

        assertThat(out).contains("· 1 job(s) failing");
    }

    private static DashboardRenderer rendererFor(StateService state, UsageTracker tracker) {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        return new DashboardRenderer(new TaskViews(state, config), tracker, new Jobs(List.of()));
    }
}