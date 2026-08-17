package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.model.MergeRequestRef;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskRepo;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipServiceTest {

    private final StateService stateService = mock(StateService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final GitService gitService = mock(GitService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final CodeHost host = mock(CodeHost.class);

    @BeforeEach
    void oneProjectOnGitLabAndTasksAddressedByTheirId() {
        when(stateService.canonicalTaskId(anyString())).thenAnswer(call -> call.getArgument(0));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(host.displayName()).thenReturn("GitLab");
        when(host.hostsRepository(anyString())).thenReturn(true);
        when(gitService.commitAll(any(), any(), anyString())).thenReturn(new GitService.Commit(true, 3));
    }

    /**
     * A reply does not resolve a thread, and the next round relays every unresolved one — so without this the
     * comments an agent pushed back on come back forever. Resolving a disagreement would hide it instead, which
     * is why the rule is about what was FIXED, and why it cannot appear where nothing is posted at all.
     */
    @Test
    void tellsTheAgentToResolveOnlyTheThreadsItActuallyFixed() {
        var posting = ConfigService.ConfigFile.defaults();
        var notPosting = ConfigService.ConfigFile.defaults().withCodeReview(
                posting.codeReview().withPostReviewReplies(false));

        assertThat(ShipService.repliesStep(posting))
                .contains("Resolve a thread ONLY where you changed the code it asked for")
                .contains("pushed back on or asked about UNRESOLVED");
        assertThat(ShipService.repliesStep(notPosting)).doesNotContain("Resolve a thread");
    }

    @Test
    void commitsPushesAndOpensTheReviewRequestWithoutInvolvingTheAgent() {
        // The whole point: no prose relayed to a model, so there is nobody to stall on a permission prompt
        // and nothing to reword. SHIPPING stops being a state a task can hang in.
        havingTask("ABC-42", TaskStatus.REVIEW_PENDING, null, "Widget layout is off");
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/9", true)));

        String result = ship().ship("ABC-42");

        ArgumentCaptor<MergeRequestSpec> spec = ArgumentCaptor.captor();
        verify(gitService).commitAll(any(), eq(Path.of("/wt")), eq("ABC-42 Widget layout is off"));
        verify(gitService).pushBranch(any(), eq(Path.of("/wt")), eq("ABC-42"));
        verify(host).createOrUpdateMergeRequest(spec.capture());
        assertThat(spec.getValue().sourceBranch()).isEqualTo("ABC-42");
        assertThat(spec.getValue().targetBranch()).isEqualTo("main");     // never the origin/ spelling
        // The status move is recorded as a review ROUND (history + a fresh polling window), not as a generic
        // status update — see recordsAnotherRoundShippedOntoTheSameRequestAsARealRound.
        verify(stateService).updateTask(eq("ABC-42"), any());
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
        verify(sessions, never()).appendTaskContext(anyString(), anyString());
        assertThat(result).contains("committed 3 file(s)", "pushed", "opened https://host/mr/9", "CI_POLLING");
    }

    @Test
    void targetsTheReviewRequestAtTheTasksOwnBaseBranchWhenItHasOne() {
        when(stateService.task("ABC-7")).thenReturn(Optional.of(TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).mrUrl(null).remoteUrl("git@host:demo/demo.git").title("t")
                .baseBranch("feature/parent").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/12", true)));

        ship().ship("ABC-7");

        ArgumentCaptor<MergeRequestSpec> spec = ArgumentCaptor.captor();
        verify(host).createOrUpdateMergeRequest(spec.capture());
        assertThat(spec.getValue().targetBranch()).isEqualTo("feature/parent");
    }

    @Test
    void commitsAReviewRoundWithAMechanicalMessageBecauseTheBackendCannotDescribeTheFix() {
        havingTask("ABC-42", TaskStatus.CI_FAILED, "https://host/mr/9", "Widget layout is off");
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/9", false)));

        String result = ship().ship("ABC-42");

        verify(gitService).commitAll(any(), any(), eq("ABC-42 address review comments"));
        assertThat(result).contains("updated https://host/mr/9");
    }

    @Test
    void saysThePushLandedWhenTheHostThenRefusesTheRequest() {
        // Losing that distinction would send the human looking for an unpushed branch.
        havingTask("ABC-42", TaskStatus.REVIEW_PENDING, null, "t");
        when(host.createOrUpdateMergeRequest(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ship().ship("ABC-42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pushed branch ABC-42")
                .hasMessageContaining("GitLab would not open the review request");
    }

    @Test
    void fallsBackToTheAgentRelayForARepositoryNoConfiguredHostOwns() {
        // An unconfigured setup must behave exactly as it always did — opening a request needs an API jagt
        // does not otherwise have.
        havingTask("ABC-42", TaskStatus.REVIEW_PENDING, null, "Widget layout is off");
        when(host.hostsRepository(anyString())).thenReturn(false);

        String result = ship().ship("ABC-42");

        verify(sessions).writeTaskContext(eq("ABC-42"), contains("This IS the human approval to ship"));
        verify(gitService, never()).pushBranch(any(), any(), anyString());
        assertThat(result).contains("approval relayed", "orchestrator.code-host");
    }

    @Test
    void refusesToShipATaskThatHasNothingToShipOnto() {
        havingTask("ABC-42", TaskStatus.NEW, null, "t");

        assertThatThrownBy(() -> ship().ship("ABC-42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ship: ABC-42 is NEW");
        verify(gitService, never()).commitAll(any(), any(), anyString());
    }

    @Test
    void refusesASecondShipWhileTheFirstIsStillRunning() {
        havingTask("ABC-42", TaskStatus.REVIEW_PENDING, null, "t");
        ShipService shipService = ship();
        var reentrant = new java.util.concurrent.atomic.AtomicReference<String>();
        when(host.createOrUpdateMergeRequest(any())).thenAnswer(call -> {
            reentrant.set(shipService.ship("ABC-42"));
            return Optional.of(new MergeRequestRef("https://host/mr/9", true));
        });

        shipService.ship("ABC-42");

        assertThat(reentrant.get()).contains("already running");
        verify(gitService, never()).commitAll(any(), any(), eq("t"));   // the inner call did nothing
        verify(host).createOrUpdateMergeRequest(any());
    }

    @Test
    void asksTheAgentToPostItsDraftedRepliesAfterTheRequestIsUpToDate(@org.junit.jupiter.api.io.TempDir Path worktree)
            throws java.io.IOException {
        // Off the critical path on purpose: posting a reply needs the thread it answers, which the sweep does
        // not carry, but a dead agent must no longer be able to block the ship itself.
        java.nio.file.Files.writeString(worktree.resolve("review_replies.md"), "- reply");
        when(stateService.task("ABC-42")).thenReturn(Optional.of(TaskState.builder("demo", worktree.toString(),
                TaskStatus.CI_FAILED).mrUrl("https://host/mr/9").remoteUrl("git@host:demo/demo.git")
                .title("t").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/9", false)));

        String result = ship().ship("ABC-42");

        // APPENDS: a sweep may have just relayed a brief, and truncating it would lose the comments.
        verify(sessions).appendTaskContext(eq("ABC-42"), contains("NOTHING to commit or push"));
        assertThat(result).contains("asked the agent to post the drafted replies");
    }

    @Test
    void leavesTheDraftsAloneWhenTheHumanTurnedPostingOff(@org.junit.jupiter.api.io.TempDir Path worktree)
            throws java.io.IOException {
        java.nio.file.Files.writeString(worktree.resolve("review_replies.md"), "- reply");
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())))
                .withCodeReview(ConfigService.ConfigFile.CodeReviewConfig.defaults()
                        .withPostReviewReplies(false)));
        when(stateService.task("ABC-42")).thenReturn(Optional.of(TaskState.builder("demo", worktree.toString(),
                TaskStatus.CI_FAILED).mrUrl("https://host/mr/9").remoteUrl("git@host:demo/demo.git")
                .title("t").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/9", false)));

        String result = ship().ship("ABC-42");

        verify(sessions, never()).writeTaskContext(anyString(), anyString());
        verify(sessions, never()).appendTaskContext(anyString(), anyString());
        assertThat(result).contains("left for you to post");
    }

    @Test
    void firstShipTellsTheRelayedAgentTheExactTitleAndToOpenTheRequest() {
        String instruction = ShipService.shipInstruction(true, "ABC-42 Widget layout is off", "ABC-42", "dev", "");

        assertThat(instruction).contains("EXACTLY this message: \"ABC-42 Widget layout is off\"")
                .contains("create one via your code-host MCP");
    }

    @Test
    void relayedReviewRoundLeadsWithTheTaskIdAndKeepsTheRequestTitle() {
        String instruction = ShipService.shipInstruction(false, "ABC-42 Widget layout is off", "ABC-42", "dev", "");

        assertThat(instruction).contains("STARTS with \"ABC-42\"")
                .contains("do NOT create a new one or retitle it")
                .doesNotContain("EXACTLY this message");
    }

    @Test
    void recordsAnotherRoundShippedOntoTheSameRequestAsARealRound() {
        // The path CLAUDE.md calls normal ("the human iterates and ships another round onto the same request")
        // does not change the status, so it used to leave no history entry and no fresh polling window.
        havingTask("ABC-42", TaskStatus.CI_POLLING, "https://host/mr/9", "Widget layout is off");
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/9", false)));

        ship().ship("ABC-42");

        ArgumentCaptor<java.util.function.UnaryOperator<TaskState>> update = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-42"), update.capture());
        TaskState before = TaskState.builder("demo", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/9").mrCreatedAt(1_000L).lastPolledAt(9_000L).build();
        TaskState after = update.getValue().apply(before);
        assertThat(after.history()).hasSizeGreaterThan(before.history().size());
        assertThat(after.mrCreatedAt()).isGreaterThan(1_000L);
        assertThat(after.lastPolledAt()).isZero();
    }

    @Test
    void opensARequestInEveryRepositoryTheTaskWorksInAndRecordsOneRound() {
        when(configService.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/release", "dev",
                List.of()));
        when(stateService.task("ABC-5")).thenReturn(Optional.of(TaskState.builder(List.of(
                        new TaskRepo("demo", "/wt", "git@host:demo/demo.git", null, null),
                        new TaskRepo("web", "/web-wt", "git@host:demo/web.git", null, null)),
                TaskStatus.REVIEW_PENDING).title("Widget layout is off").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/1", true)),
                        Optional.of(new MergeRequestRef("https://host/mr/2", true)));

        String result = ship().ship("ABC-5");

        ArgumentCaptor<MergeRequestSpec> specs = ArgumentCaptor.captor();
        verify(host, times(2)).createOrUpdateMergeRequest(specs.capture());
        assertThat(specs.getAllValues()).extracting(MergeRequestSpec::remoteUrl, MergeRequestSpec::targetBranch)
                .containsExactly(tuple("git@host:demo/demo.git", "main"),
                        tuple("git@host:demo/web.git", "release"));
        verify(gitService).pushBranch(any(), eq(Path.of("/wt")), eq("ABC-5"));
        verify(gitService).pushBranch(any(), eq(Path.of("/web-wt")), eq("ABC-5"));
        assertThat(result).contains("demo committed", "web committed", "https://host/mr/1",
                "https://host/mr/2");
    }

    @Test
    void keepsBothRequestsOnTheirOwnRepositoriesWhileRecordingASingleRound() {
        when(configService.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/release", "dev",
                List.of()));
        List<TaskRepo> repos = List.of(new TaskRepo("demo", "/wt", "git@host:demo/demo.git", null, null),
                new TaskRepo("web", "/web-wt", "git@host:demo/web.git", null, null));
        when(stateService.task("ABC-5")).thenReturn(Optional.of(
                TaskState.builder(repos, TaskStatus.REVIEW_PENDING).title("Widget layout is off").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/1", true)),
                        Optional.of(new MergeRequestRef("https://host/mr/2", true)));

        ship().ship("ABC-5");

        ArgumentCaptor<java.util.function.UnaryOperator<TaskState>> update = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-5"), update.capture());
        TaskState after = update.getValue().apply(TaskState.builder(repos, TaskStatus.REVIEW_PENDING).build());
        assertThat(after.repo("demo").orElseThrow().mrUrl()).isEqualTo("https://host/mr/1");
        assertThat(after.repo("web").orElseThrow().mrUrl()).isEqualTo("https://host/mr/2");
        assertThat(after.history()).hasSize(2);
    }

    @Test
    void commitsTheOpeningTitleInARepositoryThatIsStillARoundBehind() {
        when(configService.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/release", "dev",
                List.of()));
        when(stateService.task("ABC-5")).thenReturn(Optional.of(TaskState.builder(List.of(
                        new TaskRepo("demo", "/wt", "git@host:demo/demo.git", "https://host/mr/1", null),
                        new TaskRepo("web", "/web-wt", "git@host:demo/web.git", null, null)),
                TaskStatus.CI_POLLING).title("Widget layout is off").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/1", false)),
                        Optional.of(new MergeRequestRef("https://host/mr/2", true)));

        ship().ship("ABC-5");

        verify(gitService).commitAll(any(), eq(Path.of("/wt")), eq("ABC-5 address review comments"));
        verify(gitService).commitAll(any(), eq(Path.of("/web-wt")), eq("ABC-5 Widget layout is off"));
    }

    @Test
    void refusesToShipAMultiRepoTaskWhoseRepositoriesAreNotAllHosted() {
        when(configService.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/release", "dev",
                List.of()));
        when(host.hostsRepository("git@host:demo/web.git")).thenReturn(false);
        when(stateService.task("ABC-5")).thenReturn(Optional.of(TaskState.builder(List.of(
                        new TaskRepo("demo", "/wt", "git@host:demo/demo.git", null, null),
                        new TaskRepo("web", "/web-wt", "git@host:demo/web.git", null, null)),
                TaskStatus.REVIEW_PENDING).title("Widget layout is off").build()));

        assertThatThrownBy(() -> ship().ship("ABC-5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("web has no configured code host")
                .hasMessageContaining("orchestrator.code-host");

        verify(host, never()).createOrUpdateMergeRequest(any());
        verify(gitService, never()).pushBranch(any(), any(), any());
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
    }

    private ShipService ship() {
        return new ShipService(stateService, configService, gitService, sessions, List.of(host));
    }

    private void havingTask(String taskId, TaskStatus status, String requestUrl, String title) {
        when(stateService.task(taskId)).thenReturn(Optional.of(TaskState.builder("demo", "/wt", status)
                .mrUrl(requestUrl).remoteUrl("git@host:demo/demo.git").title(title).build()));
    }

    @Test
    void keepsTheRequestOfARepositoryThatLandedWhenALaterOneFails() {
        when(configService.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/release", "dev",
                List.of()));
        List<TaskRepo> repos = List.of(new TaskRepo("demo", "/wt", "git@host:demo/demo.git", null, null),
                new TaskRepo("web", "/web-wt", "git@host:demo/web.git", null, null));
        when(stateService.task("ABC-5")).thenReturn(Optional.of(
                TaskState.builder(repos, TaskStatus.REVIEW_PENDING).title("Widget layout is off").build()));
        when(host.createOrUpdateMergeRequest(any()))
                .thenReturn(Optional.of(new MergeRequestRef("https://host/mr/1", true)), Optional.empty());

        assertThatThrownBy(() -> ship().ship("ABC-5")).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<java.util.function.UnaryOperator<TaskState>> saved = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-5"), saved.capture());
        TaskState after = saved.getValue().apply(TaskState.builder(repos, TaskStatus.REVIEW_PENDING).build());
        assertThat(after.repo("demo").orElseThrow().mrUrl()).isEqualTo("https://host/mr/1");
        assertThat(after.status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }
}
