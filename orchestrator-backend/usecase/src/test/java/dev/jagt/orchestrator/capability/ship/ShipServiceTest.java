package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipServiceTest {

    private final StateService stateService = mock(StateService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);

    @BeforeEach
    void oneProjectAndTasksAddressedByTheirId() {
        when(stateService.canonicalTaskId(anyString())).thenAnswer(call -> call.getArgument(0));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(configService.project("web")).thenReturn(new ProjectConfig("/web", "origin/master", "dev", List.of()));
    }

    @Test
    void handsTheShipToTheAgentAndWaitsForTheRequestItOpens() {
        when(stateService.task("ABC-42")).thenReturn(Optional.of(TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git")
                .title("Widget layout is off").build()));

        Outcome outcome = new ShipService(stateService, configService, sessions).ship("ABC-42");

        verify(sessions).writeTaskContext(eq("ABC-42"), contains("This IS the human approval to ship"));
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        assertThat(outcome.message()).contains("relayed to the agent", "SHIPPING");
        assertThat(outcome.stamp()).isEqualTo("shipping");
    }

    @Test
    void namesTheTasksOwnBaseBranchAsWhatTheRequestMergesIntoWhenItHasOne() {
        when(stateService.task("ABC-7")).thenReturn(Optional.of(TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git").title("t")
                .baseBranch("feature/parent").build()));

        new ShipService(stateService, configService, sessions).ship("ABC-7");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-7"), instruction.capture());
        assertThat(instruction.getValue()).contains("merges into feature/parent");
    }

    @Test
    void namesEveryRepositoryTheTaskSpansSoNoneIsLeftUnshipped() {
        when(stateService.task("ABC-42")).thenReturn(Optional.of(TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("Widget layout is off")
                .repos(List.of(new TaskRepo("demo", "/wt", "git@host:demo/demo.git", null, null),
                        new TaskRepo("web", "/wt-web", "git@host:demo/web.git", null, null))).build()));

        new ShipService(stateService, configService, sessions).ship("ABC-42");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-42"), instruction.capture());
        assertThat(instruction.getValue())
                .contains("demo: /wt, merges into main")
                .contains("web: /wt-web, merges into master")
                .contains("a repository left behind is a half-shipped task");
    }

    @Test
    void refusesASecondShipWhileTheFirstIsStillRunning() {
        when(stateService.task("ABC-42")).thenReturn(Optional.of(TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git").title("t").build()));
        ShipService shipService = new ShipService(stateService, configService, sessions);
        AtomicReference<Outcome> reentrant = new AtomicReference<>();
        doAnswer(call -> {
            reentrant.set(shipService.ship("ABC-42"));
            return null;
        }).when(sessions).writeTaskContext(eq("ABC-42"), anyString());

        shipService.ship("ABC-42");

        assertThat(reentrant.get().message()).contains("already running");
        verify(sessions).writeTaskContext(eq("ABC-42"), anyString());
    }

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
    void namesTheExactTitleAndTheRepositoryWhoseRequestIsStillMissing() {
        String instruction = ShipService.shipInstruction("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false)), "");

        assertThat(instruction).contains("EXACTLY \"ABC-42 Widget layout is off\"")
                .contains("demo: /wt, merges into dev — NO request yet, open one")
                .contains("Leave the description empty");
    }

    @Test
    void asksForARoundMessageAndNoNewRequestWhereOneIsAlreadyOpen() {
        String instruction = ShipService.shipInstruction("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", true)), "");

        assertThat(instruction).contains("demo: /wt, merges into dev — its request is already open")
                .contains("STARTS with \"ABC-42\"")
                .contains("do NOT create another or retitle it");
    }

    @Test
    void tellsTheAgentWhichRepositoryOfATwoRepoTaskIsARoundBehind() {
        String instruction = ShipService.shipInstruction("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", true),
                        new ShipService.Target("web", "/wt-web", "dev", false)), "");

        assertThat(instruction).contains("demo: /wt, merges into dev — its request is already open")
                .contains("web: /wt-web, merges into dev — NO request yet, open one");
    }

    @Test
    void asksForOneLinkBackFromATaskWithASingleRepository() {
        String instruction = ShipService.shipInstruction("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false)), "");

        assertThat(instruction).contains("CI_POLLING and reviewRequestUrl=<the url>");
    }

    @Test
    void asksForOneLinkPerProjectFromATaskSpanningRepositories() {
        String instruction = ShipService.shipInstruction("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false),
                        new ShipService.Target("web", "/wt-web", "dev", false)), "");

        assertThat(instruction)
                .contains("reviewRequests={\"demo\": \"<its url>\", \"web\": \"<its url>\"}");
    }
}
