package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.MergeRequestRef;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.ProjectConfig;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipServiceTest {

    private final StateService stateService = mock(StateService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final GitService gitService = mock(GitService.class);
    private final TmuxService tmuxService = mock(TmuxService.class);
    private final OrchestratorTools tools = mock(OrchestratorTools.class);
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
        verify(tools).updateAgentStatus("CI_POLLING", "MR: https://host/mr/9", "ABC-42", null);
        verify(tools, never()).writeTaskContext(anyString(), anyString());
        assertThat(result).contains("committed 3 file(s)", "pushed", "opened https://host/mr/9", "CI_POLLING");
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
        verify(tools, never()).updateAgentStatus(anyString(), anyString(), anyString(), any());
    }

    @Test
    void fallsBackToTheAgentRelayForARepositoryNoConfiguredHostOwns() {
        // An unconfigured setup must behave exactly as it always did — opening a request needs an API jagt
        // does not otherwise have.
        havingTask("ABC-42", TaskStatus.REVIEW_PENDING, null, "Widget layout is off");
        when(host.hostsRepository(anyString())).thenReturn(false);

        String result = ship().ship("ABC-42");

        verify(tools).writeTaskContext(eq("ABC-42"), contains("This IS the human approval to ship"));
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

        verify(tools).writeTaskContext(eq("ABC-42"), contains("NOTHING to commit or push"));
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

        verify(tools, never()).writeTaskContext(anyString(), anyString());
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

    private ShipService ship() {
        return new ShipService(stateService, configService, gitService, tmuxService, tools, List.of(host));
    }

    private void havingTask(String taskId, TaskStatus status, String requestUrl, String title) {
        when(stateService.task(taskId)).thenReturn(Optional.of(TaskState.builder("demo", "/wt", status)
                .mrUrl(requestUrl).remoteUrl("git@host:demo/demo.git").title(title).build()));
    }
}
