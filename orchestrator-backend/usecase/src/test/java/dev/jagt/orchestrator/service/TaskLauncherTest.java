package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskLauncherTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final TaskLaunches launches = mock(TaskLaunches.class);
    private final TaskLauncher launcher = new TaskLauncher(configService, launches, mock(TaskResume.class));

    @Test
    void createsOneTaskAcrossEveryProjectNamedInTheSameTokenTheFirstHoldingTheSession() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of()),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of()))));
        LaunchRequest request = LaunchRequest.of("ABC-1").withProject("web,api").normalized();

        launcher.launch(request);

        verify(launches).ticket(request, List.of("web", "api"));
    }

    @Test
    void leavesAnItemNobodyNamedAProjectForToTheRouter() {
        launcher.launch(LaunchRequest.of("ABC-42"));

        verify(launches).ticket(LaunchRequest.of("ABC-42"), null);
    }

    @Test
    void handsIntakesPaidReadOnWithTheProjectItPlacedTheItemIn() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        LaunchRequest request = LaunchRequest.of("ABC-42").withProject("group-a");
        Answer<TicketFacts> read = new Answer<>(Optional.of(TicketFacts.defaults().withKey("ABC-42")),
                TokenUsage.NONE);

        launcher.launch(request, read);

        verify(launches).ticket(request, List.of("group-a"), read);
    }

    @Test
    void refusesTheLaunchWhenOneOfTheNamedProjectsIsNotConfigured() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("api", new ProjectConfig("/p", "origin/main", "dev", List.of()))));

        assertThatThrownBy(() -> launcher.launch(LaunchRequest.of("ABC-1").withProject("api,typo").normalized()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown project [typo]");

        verifyNoInteractions(launches);
    }

    @Test
    void makesATaskOfATypedLineThatOpensOnAProjectAndNamesNoTicket() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));

        launcher.launchLine("group-a tighten the parser");

        ArgumentCaptor<LaunchRequest> written = ArgumentCaptor.forClass(LaunchRequest.class);
        verify(launches).written(written.capture(), eq(List.of("group-a")));
        assertThat(written.getValue().notes()).isEqualTo("tighten the parser");
    }

    @Test
    void refusesATaskWithNeitherATicketNorAnythingToDo() {
        var refused = launcher.launch(LaunchRequest.defaults().withProject("group-a"));

        assertThat(refused.created()).isFalse();
        assertThat(refused.message()).contains("nothing to do");
        verifyNoInteractions(launches);
    }
}
