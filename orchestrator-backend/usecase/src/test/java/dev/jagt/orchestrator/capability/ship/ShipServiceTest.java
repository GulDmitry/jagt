package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import dev.jagt.orchestrator.flow.Refusal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private final StepRelay relay = mock(StepRelay.class);
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
                TaskStatus.REVIEW_PENDING)
                .title("Widget layout is off").build());

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        verify(relay).write(eq("ABC-42"), eq("ship"), contains("Push branch ABC-42."));
        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        assertThat(outcome.message()).contains("relayed to the agent", "SHIPPING");
        assertThat(outcome.stamp()).isEqualTo("shipping");
    }

    @Test
    void namesTheTasksOwnBaseBranchAsWhatTheRequestMergesIntoWhenItHasOne() {
        stateService.putTask("ABC-7", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t")
                .baseBranch("feature/parent").build());

        new ShipService(stateService, configService, relay, changes).ship("ABC-7");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(relay).write(eq("ABC-7"), eq("ship"), instruction.capture());
        assertThat(instruction.getValue()).contains("merges into feature/parent");
    }

    @Test
    void namesEveryRepositoryTheTaskSpansSoNoneIsLeftUnshipped() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("Widget layout is off")
                .repos(List.of(TaskRepo.of("demo", "/wt").withRemoteUrl("git@host:demo/demo.git"),
                        TaskRepo.of("web", "/wt-web").withRemoteUrl("git@host:demo/web.git"))).build());

        new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(relay).write(eq("ABC-42"), eq("ship"), instruction.capture());
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

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        ArgumentCaptor<String> instruction = ArgumentCaptor.captor();
        verify(relay).write(eq("ABC-42"), eq("ship"), instruction.capture());
        assertThat(instruction.getValue()).contains("demo: /wt").doesNotContain("web: /wt-web");
        assertThat(outcome.message()).contains("nothing to ship in web");
    }

    @Test
    void shipsNothingWhenNoRepositoryOfTheTaskHoldsWork() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t").build());
        when(changes.holdsWork("demo", "/wt", "main")).thenReturn(false);

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
        assertThat(outcome.message()).contains("nothing to ship");
        verify(relay, never()).write(anyString(), anyString(), anyString());
    }

    @Test
    void shipsARepositoryAlreadyInReviewEvenWhenItHoldsNothingNew() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t")
                .repos(List.of(new TaskRepo("demo", "/wt", "git@host:demo/demo.git",
                        "https://code.example/demo/-/merge_requests/1", null))).build());
        when(changes.holdsWork("demo", "/wt", "main")).thenReturn(false);

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        verify(relay).write(eq("ABC-42"), eq("ship"), contains("its request is already open"));
    }

    @Test
    void refusesASecondShipWhileTheFirstIsStillRunning() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t").build());
        ShipService shipService = new ShipService(stateService, configService, relay, changes);
        AtomicReference<Outcome> reentrant = new AtomicReference<>();
        doAnswer(call -> {
            reentrant.set(shipService.ship("ABC-42"));
            return null;
        }).when(relay).write(eq("ABC-42"), eq("ship"), anyString());

        shipService.ship("ABC-42");

        assertThat(reentrant.get().message()).contains("already running");
        verify(relay).write(eq("ABC-42"), eq("ship"), anyString());
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
        String instruction = ShipService.shipFacts("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false)), "");

        assertThat(instruction).contains("EXACTLY \"ABC-42 Widget layout is off\"")
                .contains("demo: /wt, merges into dev — NO request yet, open one");
    }

    @Test
    void asksForARoundMessageAndNoNewRequestWhereOneIsAlreadyOpen() {
        String instruction = ShipService.shipFacts("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", true)), "");

        assertThat(instruction).contains("demo: /wt, merges into dev — its request is already open")
                .contains("STARTS with \"ABC-42\"");
    }

    @Test
    void tellsTheAgentWhichRepositoryOfATwoRepoTaskIsARoundBehind() {
        String instruction = ShipService.shipFacts("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", true),
                        new ShipService.Target("web", "/wt-web", "dev", false)), "");

        assertThat(instruction).contains("demo: /wt, merges into dev — its request is already open")
                .contains("web: /wt-web, merges into dev — NO request yet, open one");
    }

    @Test
    void asksForOneLinkBackFromATaskWithASingleRepository() {
        String instruction = ShipService.shipFacts("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false)), "");

        assertThat(instruction).contains("CI_POLLING and reviewRequestUrl=<the url>")
                .doesNotContain("half-shipped");
    }

    @Test
    void asksForOneLinkPerProjectFromATaskSpanningRepositories() {
        String instruction = ShipService.shipFacts("ABC-42 Widget layout is off", "ABC-42",
                List.of(new ShipService.Target("demo", "/wt", "dev", false),
                        new ShipService.Target("web", "/wt-web", "dev", false)), "");

        assertThat(instruction)
                .contains("reviewRequests={\"demo\": \"<its url>\", \"web\": \"<its url>\"}");
    }

    @Test
    void speaksOfOneRequestWhereOnlyOneRepositoryCarriesWork() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t").build());

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        assertThat(outcome.message()).contains("until it reports the request").doesNotContain("requests");
    }

    @Test
    void speaksOfRequestsWhereSeveralRepositoriesCarryWork() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t")
                .repos(List.of(TaskRepo.of("demo", "/wt").withRemoteUrl("git@host:demo/demo.git"),
                        TaskRepo.of("web", "/wt-web").withRemoteUrl("git@host:demo/web.git"))).build());

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        assertThat(outcome.message()).contains("until it reports the requests");
    }

    @Test
    void saysTheRepliesAreTheHumansToPostWherePostingWasTurnedOff() {
        stateService.putTask("ABC-42", TaskState.builder("demo", "/wt",
                TaskStatus.REVIEW_PENDING).title("t").build());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())))
                .withCodeReview(ConfigService.ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false)));

        Outcome outcome = new ShipService(stateService, configService, relay, changes).ship("ABC-42");

        assertThat(outcome.message()).contains("review_replies.md is yours to post (postReviewReplies=false)");
    }

    @Test
    void refusesATaskThatIsNotThereNamingIt() {
        assertThatThrownBy(() -> new ShipService(stateService, configService, relay, changes).ship("ABC-9"))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("No task ABC-9")
                .extracting("code").isEqualTo(Refusal.Code.NO_SUCH_TASK);
    }
}
