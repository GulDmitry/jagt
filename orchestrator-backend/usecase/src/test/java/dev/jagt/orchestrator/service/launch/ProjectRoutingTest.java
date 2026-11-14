package dev.jagt.orchestrator.service.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.assistant.Assistants;
import dev.jagt.orchestrator.service.task.FinishedTasks;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.RoutingAnswer;
import dev.jagt.orchestrator.task.launch.RoutingQuestion;
import dev.jagt.orchestrator.task.launch.RulePair;
import dev.jagt.orchestrator.task.launch.TicketFacts;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ProjectRoutingTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final Assistants assistants = mock(Assistants.class, RETURNS_DEEP_STUBS);
  private final FinishedTasks finished = mock(FinishedTasks.class);
  @TempDir Path root;
  private final ArgumentCaptor<RoutingQuestion> asked = ArgumentCaptor.captor();
  private RoutingMemory memory;
  private ProjectRouting routing;
  private FinishedTask quoteImportByApi;
  private FinishedTask guessedByRouter;
  private FinishedTask quoteImportByRouter;
  private FinishedTask quoteImportByHuman;
  private FinishedTask otherByRouter;

  @BeforeEach
  void setUp() {
    memory =
        new RoutingMemory(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())),
            Clock.systemUTC());
    routing = new ProjectRouting(configService, assistants, finished, memory);
    quoteImportByApi =
        FinishedTask.of(
            "ABC-1",
            TaskState.builder("api", "/wt", TaskStatus.DONE)
                .title("Quote import")
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD)))
                .build(),
            2L);
    guessedByRouter =
        FinishedTask.of(
            "ABC-2",
            TaskState.builder("web", "/wt", TaskStatus.DONE)
                .title("Guessed one")
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                .build(),
            2L);
    quoteImportByRouter =
        FinishedTask.of(
            "ABC-42",
            TaskState.builder("web", "/wt", TaskStatus.DONE)
                .title("Quote import")
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                .build(),
            2L);
    quoteImportByHuman =
        FinishedTask.of(
            "ABC-42",
            TaskState.builder("web", "/wt", TaskStatus.DONE)
                .title("Quote import")
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD)))
                .build(),
            2L);
    otherByRouter =
        FinishedTask.of(
            "ABC-7",
            TaskState.builder("web", "/wt", TaskStatus.DONE)
                .title("Other")
                .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                .build(),
            2L);
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                        "web",
                            new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
    when(finished.all()).thenReturn(List.of());
    when(assistants.routing().routeProject(any()))
        .thenReturn(new Answer<>(Optional.of(new RoutingAnswer("api", "")), TokenUsage.NONE));
  }

  @Test
  void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
    TicketFacts facts =
        TicketFacts.defaults()
            .withExists(true)
            .withKey("ABC-1")
            .withTitle("Some ticket title")
            .withTrackerProject("ABC")
            .withLabels(List.of("area-x", "no-test", "backend"));

    List<String> matches =
        ProjectRouting.projectsMatching(
            facts, Map.of("group-a", List.of("backend"), "group-b", List.of("frontend")));

    assertThat(matches).containsExactly("group-a");
  }

  @Test
  void buysNoRoutingCallWhenThereIsNothingToChooseBetween() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));

    assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
        .isEqualTo(new ProjectRouting.Placed(List.of("api")));
    verify(assistants.routing(), never()).routeProject(any());
  }

  @Test
  void placesAnItemWhoseLabelsNameOneRepositoryWithoutAskingTheRouter() {
    TicketFacts item = TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend"));

    assertThat(routing.projectFor(item)).isEqualTo(new ProjectRouting.Placed(List.of("api")));
    verify(assistants.routing(), never()).routeProject(any());
  }

  @Test
  void placesAnItemInEveryRepositoryItsLabelsNameWithoutAskingTheRouter() {
    TicketFacts item =
        TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend", "frontend"));

    assertThat(routing.projectFor(item).projects()).containsExactlyInAnyOrder("api", "web");
    verify(assistants.routing(), never()).routeProject(any());
  }

  @Test
  void showsTheRouterOnlyThePlacementsSomethingConfirmed() {
    when(finished.all()).thenReturn(List.of(quoteImportByApi, guessedByRouter));

    routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

    verify(assistants.routing()).routeProject(asked.capture());
    assertThat(asked.getValue().precedents()).containsExactly("- ABC-1 \"Quote import\" → api");
  }

  @Test
  void writesDownWhatPlacedAnItemNoLabelCouldPlace() {
    when(assistants.routing().routeProject(any()))
        .thenReturn(
            new Answer<>(
                Optional.of(new RoutingAnswer("api", "ABC items about quote import")),
                TokenUsage.NONE));

    routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

    assertThat(memory.rules()).containsExactly("ABC items about quote import -> api");
  }

  @Test
  void mergesTheWordingsOneRuleWasWrittenInOnceTheMemoryIsFull() throws IOException {
    when(assistants.routing().routeProject(any()))
        .thenReturn(
            new Answer<>(
                Optional.of(new RoutingAnswer("api", "importing quotes")), TokenUsage.NONE));
    Files.createDirectories(root.resolve("memory"));
    Files.writeString(
        root.resolve("memory/routing.md"),
        "quote import -> api\n"
            + IntStream.rangeClosed(1, 58)
                .mapToObj(n -> "used rule " + n + " -> web #1\n")
                .collect(Collectors.joining()));
    when(assistants.routing().sameRules(any()))
        .thenReturn(
            new Answer<>(
                Optional.of(new RulePair("quote import -> api", "importing quotes -> api")),
                TokenUsage.NONE));

    routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

    assertThat(memory.rules())
        .contains("quote import -> api")
        .doesNotContain("importing quotes -> api");
  }

  @Test
  void retiresTheRuleHumanHasJustContradicted() {
    memory.remember("ABC items about quote import", "web");
    when(finished.all()).thenReturn(List.of(quoteImportByRouter));
    when(assistants
            .routing()
            .staleRule("ABC-42", "api", List.of("ABC items about quote import -> web")))
        .thenReturn(
            new Answer<>(Optional.of("ABC items about quote import -> web"), TokenUsage.NONE));

    routing.placedByHand("ABC-42", "api");

    assertThat(memory.rules()).isEmpty();
  }

  @Test
  void asksNothingWhereTheRouterHadNeverPlacedThatItemItself() {
    memory.remember("ABC items about quote import", "web");
    when(finished.all()).thenReturn(List.of(quoteImportByHuman));

    routing.placedByHand("ABC-42", "api");

    verify(assistants.routing(), never()).staleRule(anyString(), anyString(), any());
    assertThat(memory.rules()).containsExactly("ABC items about quote import -> web");
  }

  @Test
  void tellsTheRouterWhatEachRepositoryIs() {

    routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

    verify(assistants.routing()).routeProject(asked.capture());
    assertThat(asked.getValue().projects())
        .containsOnly(Map.entry("api", "/api"), Map.entry("web", "/web"));
  }

  @Test
  void asksNothingWhereOnlyAnotherItemWasPlacedByTheRouter() {
    memory.remember("ABC items about quote import", "web");
    when(finished.all()).thenReturn(List.of(quoteImportByHuman, otherByRouter));

    routing.placedByHand("ABC-42", "api");

    verify(assistants.routing(), never()).staleRule(anyString(), anyString(), any());
  }

  @Test
  void leavesTheItemForHumanWhenTheRouterNamedRepositoryJagtDoesNotHave() {
    when(assistants.routing().routeProject(any()))
        .thenReturn(new Answer<>(Optional.of(new RoutingAnswer("mobile", "")), TokenUsage.NONE));

    assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
        .isInstanceOf(ProjectRouting.Undecided.class);
  }

  @Test
  void reportsRouterThatAnsweredNothingAsUnreadableRatherThanUndecided() {
    when(assistants.routing().routeProject(any())).thenReturn(Answer.unavailable());

    assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
        .isInstanceOf(ProjectRouting.Unreadable.class);
  }
}
