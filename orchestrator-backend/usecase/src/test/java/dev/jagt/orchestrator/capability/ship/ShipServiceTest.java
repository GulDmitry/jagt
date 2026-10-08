package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipServiceTest {

    @TempDir
    Path root;
    private StateService stateService;
    private final ConfigService configService = mock(ConfigService.class);
    private final AgentSessions sessions = mock(AgentSessions.class);
    private final WorktreeChanges changes = mock(WorktreeChanges.class);

    @BeforeEach
    void oneProjectAndTasksAddressedByTheirId() {
        stateService = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(configService.project("web")).thenReturn(new ProjectConfig("/web", "origin/master", "dev", List.of()));
        when(changes.holdsWork(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void handsTheShipToTheAgentAndWaitsForTheRequestItOpens() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git")
                .title("Widget layout is off").build());

        Outcome outcome = new ShipService(stateService, configService, sessions, changes).ship("ABC-42");

        verify(sessions).writeTaskContext(eq("ABC-42"), contains("This IS the human approval to ship"));
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        assertThat(outcome.message()).contains("relayed to the agent", "SHIPPING");
        assertThat(outcome.stamp()).isEqualTo("shipping");
    }

    @Test
    void namesTheTasksOwnBaseBranchAsWhatTheRequestMergesIntoWhenItHasOne() {
        stateService.putTask("ABC-7", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git").title("t")
                .baseBranch("feature/parent").build());

        new ShipService(stateService, configService, sessions, changes).ship("ABC-7");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-7"), instruction.capture());
        assertThat(instruction.getValue()).contains("merges into feature/parent");
    }

    @Test
    void namesEveryRepositoryTheTaskSpansSoNoneIsLeftUnshipped() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("Widget layout is off")
                .repos(List.of(TaskRepo.of("demo", "/wt").withRemoteUrl("git@host:demo/demo.git"),
                        TaskRepo.of("web", "/wt-web").withRemoteUrl("git@host:demo/web.git"))).build());

        new ShipService(stateService, configService, sessions, changes).ship("ABC-42");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-42"), instruction.capture());
        assertThat(instruction.getValue())
                .contains("demo: /wt, merges into main")
                .contains("web: /wt-web, merges into master")
                .contains("a repository left behind is a half-shipped task");
    }

    @Test
    void leavesOutTheRepositoryHoldingNothingSoNoEmptyRequestIsOpened() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("Widget layout is off")
                .repos(List.of(TaskRepo.of("demo", "/wt").withRemoteUrl("git@host:demo/demo.git"),
                        TaskRepo.of("web", "/wt-web").withRemoteUrl("git@host:demo/web.git"))).build());
        when(changes.holdsWork("web", "/wt-web", "master")).thenReturn(false);

        Outcome outcome = new ShipService(stateService, configService, sessions, changes).ship("ABC-42");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(sessions).writeTaskContext(eq("ABC-42"), instruction.capture());
        assertThat(instruction.getValue()).contains("demo: /wt").doesNotContain("web: /wt-web");
        assertThat(outcome.message()).contains("nothing to ship in web");
    }

    @Test
    void shipsNothingWhenNoRepositoryOfTheTaskHoldsWork() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git").title("t").build());
        when(changes.holdsWork("demo", "/wt", "main")).thenReturn(false);

        Outcome outcome = new ShipService(stateService, configService, sessions, changes).ship("ABC-42");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
        assertThat(outcome.message()).contains("nothing to ship");
        verify(sessions, never()).writeTaskContext(anyString(), anyString());
    }

    @Test
    void shipsARepositoryAlreadyInReviewEvenWhenItHoldsNothingNew() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t")
                .repos(List.of(new TaskRepo("demo", "/wt", "git@host:demo/demo.git",
                        "https://code.example/demo/-/merge_requests/1", null))).build());
        when(changes.holdsWork("demo", "/wt", "main")).thenReturn(false);

        Outcome outcome = new ShipService(stateService, configService, sessions, changes).ship("ABC-42");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        verify(sessions).writeTaskContext(eq("ABC-42"), contains("its request is already open"));
    }

    @Test
    void refusesASecondShipWhileTheFirstIsStillRunning() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).remoteUrl("git@host:demo/demo.git").title("t").build());
        ShipService shipService = new ShipService(stateService, configService, sessions, changes);
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
                .contains("Resolve threads exactly as your `<review_replies>` rules say");
        assertThat(ShipService.repliesStep(notPosting)).doesNotContain("Resolve threads");
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
    void saysTheApprovalIsSpentPerRepositoryOnceTheLinksHaveBeenReportedBack() {
        String instruction = ShipService.shipInstruction("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false),
                        new ShipService.Target("web", "/wt-web", "dev", false)), "");

        assertThat(instruction).contains("ONE commit and ONE push PER REPOSITORY listed above")
                .contains("single-use and does not clear this file");
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
