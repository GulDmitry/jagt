package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
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
import org.mockito.ArgumentCaptor;

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
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final FinishedTasks finished = mock(FinishedTasks.class);
    private final RoutingMemory memory = mock(RoutingMemory.class);
    private final ProjectRouting routing = new ProjectRouting(configService, assistant, finished, memory);

    @Test
    void buysNoRoutingCallWhenThereIsNothingToChooseBetween() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(
                Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
                .isEqualTo(new ProjectRouting.Placed("api"));
        verify(assistant, never()).routeProject(any());
    }

    @Test
    void placesAnItemWhoseLabelsNameOneRepositoryWithoutAskingTheRouter() {
        TicketFacts item = TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));

        assertThat(routing.projectFor(item)).isEqualTo(new ProjectRouting.Placed("api"));
        verify(assistant, never()).routeProject(any());
    }

    @Test
    void asksTheRouterWhereTheLabelsNameMoreThanOneRepository() {
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
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(new Answer<>(
                Optional.of(new RoutingAnswer("api", "PAN items about quote import")), TokenUsage.NONE));

        routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

        verify(memory).remember("PAN items about quote import", "api");
    }

    @Test
    void mergesTheWordingsOneRuleWasWrittenInOnceTheMemoryIsFull() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(new Answer<>(
                Optional.of(new RoutingAnswer("api", "importing quotes")), TokenUsage.NONE));
        when(memory.remember("importing quotes", "api")).thenReturn(true);
        when(memory.full()).thenReturn(true);
        when(memory.rules()).thenReturn(List.of("quote import -> api", "importing quotes -> api"));
        when(assistant.sameRules(List.of("quote import -> api", "importing quotes -> api"))).thenReturn(new Answer<>(
                Optional.of(new RulePair("quote import -> api", "importing quotes -> api")), TokenUsage.NONE));

        routing.projectFor(TicketFacts.defaults().withKey("ABC-42"));

        verify(memory).merge("quote import -> api", "importing quotes -> api");
    }

    @Test
    void retiresTheRuleAHumanHasJustContradicted() {
        when(memory.rules()).thenReturn(List.of("PAN items about quote import -> web"));
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-42",
                TaskState.builder("web", "/wt", TaskStatus.DONE).title("Quote import")
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.TRACKER)))
                        .build(), 2L)));
        when(assistant.staleRule("ABC-42", "api", List.of("PAN items about quote import -> web")))
                .thenReturn(new Answer<>(Optional.of("PAN items about quote import -> web"), TokenUsage.NONE));

        routing.placedByHand("ABC-42", "api");

        verify(memory).retire("PAN items about quote import -> web");
    }

    @Test
    void asksNothingWhereTheRouterHadNeverPlacedThatItemItself() {
        when(memory.rules()).thenReturn(List.of("PAN items about quote import -> web"));
        when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-42",
                TaskState.builder("web", "/wt", TaskStatus.DONE).title("Quote import")
                        .history(List.of(new StatusChange(TaskStatus.NEW, 1L, ActionOrigin.BOARD)))
                        .build(), 2L)));

        routing.placedByHand("ABC-42", "api");

        verify(assistant, never()).staleRule(anyString(), anyString(), any());
        verify(memory, never()).retire(anyString());
    }

    @Test
    void leavesTheItemForAHumanWhenTheRouterNamedARepositoryJagtDoesNotHave() {
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
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(finished.all()).thenReturn(List.of());
        when(assistant.routeProject(any())).thenReturn(Answer.unavailable());

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42")))
                .isInstanceOf(ProjectRouting.Unreadable.class);
    }
}
