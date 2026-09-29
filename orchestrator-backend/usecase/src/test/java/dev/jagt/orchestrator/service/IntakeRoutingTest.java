package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeRoutingTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final IntakeRouting routing = new IntakeRouting(configService, assistant);

    @Test
    void buysNoRoutingCallWhenThereIsNothingToChooseBetween() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(
                Map.of("api", new ProjectConfig("/api", "origin/main", "dev", List.of()))));

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42"))).contains("api");
        verify(assistant, never()).routeProject(any(), any(), any());
    }

    @Test
    void confirmsTheLabelsRatherThanTakingThemEvenWhenTheyNameOneRepository() {
        TicketFacts item = TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(assistant.routeProject(any(), any(), any()))
                .thenReturn(new Answer<>(Optional.of("api"), TokenUsage.NONE));

        assertThat(routing.projectFor(item)).contains("api");
        verify(assistant).routeProject(eq(item), any(), eq(List.of("api")));
    }

    @Test
    void takesARepositoryTheLabelsDidNotSuggestWhenTheItemItselfSaysSo() {
        TicketFacts item = TicketFacts.defaults().withKey("ABC-42").withLabels(List.of("backend"));
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(assistant.routeProject(any(), any(), any()))
                .thenReturn(new Answer<>(Optional.of("web"), TokenUsage.NONE));

        assertThat(routing.projectFor(item)).contains("web");
    }

    @Test
    void leavesTheItemForAHumanWhenTheRouterNamedARepositoryJagtDoesNotHave() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(assistant.routeProject(any(), any(), any()))
                .thenReturn(new Answer<>(Optional.of("mobile"), TokenUsage.NONE));

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42"))).isEmpty();
    }

    @Test
    void leavesTheItemForAHumanWhenNothingCouldBeAskedAtAll() {
        when(configService.load()).thenReturn(ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of("backend")),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of("frontend")))));
        when(assistant.routeProject(any(), any(), any())).thenReturn(Answer.unavailable());

        assertThat(routing.projectFor(TicketFacts.defaults().withKey("ABC-42"))).isEmpty();
    }
}
