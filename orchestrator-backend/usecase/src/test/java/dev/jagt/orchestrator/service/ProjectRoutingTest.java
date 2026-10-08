package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.RoutingAssistant;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RulePair;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRoutingTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final RoutingAssistant assistant = mock(RoutingAssistant.class);
    private final FinishedTasks finished = mock(FinishedTasks.class);
    @TempDir
    Path root;

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = TicketFacts.defaults().withExists(true).withKey("ABC-1").withTitle("Some ticket title")
                .withTrackerProject("ABC").withLabels(List.of("area-x", "no-test", "backend"));

        List<String> matches = ProjectRouting.projectsMatching(facts,
                Map.of("group-a", List.of("backend"), "group-b", List.of("frontend")));

        assertThat(matches).containsExactly("group-a");
    }

    @Test
    void buysNoRoutingCallWhenThereIsNothingToChooseBetween() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(
                Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
                .isEqualTo(new ProjectRouting.Placed("api"));
        verify(assistant, never()).routeProject(any());
    }

    @Test
    void placesAnItemWhoseLabelsNameOneRepositoryWithoutAskingTheRouter() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        TicketFacts item = TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));

        assertThat(routing.projectFor(item)).isEqualTo(new ProjectRouting.Placed("api"));
        verify(assistant, never()).routeProject(any());
    }

    @Test
    void asksTheRouterWhereTheLabelsNameMoreThanOneRepository() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        TicketFacts item = TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend", "frontend"));
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(new Answer<>(Optional.of(new RoutingAnswer("web", "")), TokenUsage.NONE));

        assertThat(routing.projectFor(item)).isEqualTo(new ProjectRouting.Placed("web"));
    }

    @Test
    void showsTheRouterOnlyThePlacementsSomethingConfirmed() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of(
                FinishedTask.of("ABC-1", TaskState.builder("api", "/wt", TaskStatus.DONE).title("Quote import")
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD))).build(), 2L),
                FinishedTask.of("ABC-2", TaskState.builder("web", "/wt", TaskStatus.DONE).title("Guessed one")
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                        .build(), 2L)));
        when(assistant.routeProject(any())).thenReturn(new Answer<>(Optional.of(new RoutingAnswer("api", "")), TokenUsage.NONE));
        ArgumentCaptor<RoutingQuestion> asked = ArgumentCaptor.captor();

        routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

        verify(assistant).routeProject(asked.capture());
        assertThat(asked.getValue().precedents()).containsExactly("- ABC-1 \"Quote import\" → api");
    }

    @Test
    void writesDownWhatPlacedAnItemNoLabelCouldPlace() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(new Answer<>(
                Optional.of(new RoutingAnswer("api", "ABC items about quote import")), TokenUsage.NONE));

        routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

        assertThat(memory.rules()).containsExactly("ABC items about quote import -> api");
    }

    @Test
    void mergesTheWordingsOneRuleWasWrittenInOnceTheMemoryIsFull() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 2);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(new Answer<>(
                Optional.of(new RoutingAnswer("api", "importing quotes")), TokenUsage.NONE));
        memory.remember("quote import", "api");
        when(assistant.sameRules(List.of("quote import -> api", "importing quotes -> api"))).thenReturn(new Answer<>(
                Optional.of(new RulePair("quote import -> api", "importing quotes -> api")), TokenUsage.NONE));

        routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

        assertThat(memory.rules()).containsExactly("quote import -> api");
    }

    @Test
    void retiresTheRuleAHumanHasJustContradicted() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        memory.remember("ABC items about quote import", "web");
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-42",
                TaskState.builder("web", "/wt", TaskStatus.DONE).title("Quote import")
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                        .build(), 2L)));
        when(assistant.staleRule("ABC-42", "api", List.of("ABC items about quote import -> web")))
                .thenReturn(new Answer<>(Optional.of("ABC items about quote import -> web"), TokenUsage.NONE));

        routing.placedByHand("ABC-42", "api");

        assertThat(memory.rules()).isEmpty();
    }

    @Test
    void asksNothingWhereTheRouterHadNeverPlacedThatItemItself() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        memory.remember("ABC items about quote import", "web");
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-42",
                TaskState.builder("web", "/wt", TaskStatus.DONE).title("Quote import")
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD)))
                        .build(), 2L)));

        routing.placedByHand("ABC-42", "api");

        verify(assistant, never()).staleRule(anyString(), anyString(), any());
        assertThat(memory.rules()).containsExactly("ABC items about quote import -> web");
    }

    @Test
    void leavesTheItemForAHumanWhenTheRouterNamedARepositoryJagtDoesNotHave() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(new Answer<>(Optional.of(new RoutingAnswer("mobile", "")), TokenUsage.NONE));

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
                .isInstanceOf(ProjectRouting.Undecided.class);
    }

    @Test
    void reportsARouterThatAnsweredNothingAsUnreadableRatherThanUndecided() {
        RoutingMemory memory = new RoutingMemory(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString())), 60);
        ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(Answer.unavailable());

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
                .isInstanceOf(ProjectRouting.Unreadable.class);
    }
}
