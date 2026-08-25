package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskLauncherTest {

    private final TaskProvisioning provisioning = mock(TaskProvisioning.class);
    private final TicketReader tickets = mock(TicketReader.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final TaskLauncher launcher = new TaskLauncher(provisioning, tickets, configService,
            mock(TaskResume.class));

    @BeforeEach
    void configIsReadable() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    @Test
    void namesTheTaskByTheCanonicalKeyTheReadGaveBackWhenGivenAUrl() {
        oneProject("group-a");
        when(tickets.read("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-123", "Some title", "ABC",
                        List.of(), "https://tracker.example.com/browse/ABC-123")), TokenUsage.NONE));

        launcher.launch(new LaunchRequest("https://tracker.example.com/browse/ABC-123", "group-a", null, null,
                null, null));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue())
                .extracting(NewTask::taskId, NewTask::projectKey, NewTask::title, NewTask::ticketUrl)
                .containsExactly("ABC-123", "group-a", "Some title",
                        "https://tracker.example.com/browse/ABC-123");
    }

    /** The read happens BEFORE the task exists, so it can only be charged once creation named one. */
    @Test
    void chargesTheTicketReadToTheTaskItJustNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        oneProject("group-a");
        when(tickets.read("https://tracker/ABC-123")).thenReturn(new Answer<>(
                Optional.of(new TicketFacts(true, "ABC-123", "t", "ABC", List.of(),
                        "https://tracker/ABC-123")), spent));

        launcher.launch(new LaunchRequest("https://tracker/ABC-123", "group-a", null, null, null, null));

        var order = inOrder(provisioning, tickets);
        order.verify(provisioning).initializeTask(any());
        order.verify(tickets).charge("ABC-123", spent);
    }

    /**
     * A card whose ticket link is missing cannot be repaired later — nothing can tell an item that has no link
     * from one that was never reached — so the launch says so instead of starting work on half a task.
     */
    @Test
    void createsNoTaskWhenTheTrackerSaysThereIsNoSuchItem() {
        oneProject("group-a");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(
                Optional.of(new TicketFacts(false, "", "", "", List.of(), "")),
                TokenUsage.ofCall(38_000, 0, 60, 0.41)));

        String out = launcher.launch(LaunchRequest.of("ABC-42")).message();

        assertThat(out).contains("no such item: ABC-42", "no task created");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void saysTheReadFailedInsteadOfCallingTheTicketMissing() {
        oneProject("group-a");
        when(tickets.read("ABC-42")).thenReturn(Answer.unavailable());

        assertThat(launcher.launch(LaunchRequest.of("ABC-42")).message()).contains("read failed");
    }

    @Test
    void createsNoTaskWhenTheReadAnsweredAboutADifferentItem() {
        oneProject("group-a");
        when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-99",
                "Widget layout is off", "ABC", List.of(), "https://tracker/ABC-99")), TokenUsage.NONE));

        String out = launcher.launch(LaunchRequest.of("ABC-42")).message();

        assertThat(out).contains("asked for ABC-42 and got ABC-99 back", "no task created");
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void warnsAboutALeftoverBranchWithoutSpendingATicketRead() {
        oneProject("group-a");
        when(provisioning.existingBranchProject(eq("ABC-9"), any())).thenReturn("group-a");

        String out = launcher.launch(LaunchRequest.of("ABC-9")).message();

        assertThat(out).contains("already exists in group-a", "do ABC-9 recreate", "do ABC-9 resume");
        verifyNoInteractions(tickets);
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void relaysTheHumansNotesToTheAgentAlongsideTheTicket() {
        oneProject("demo");
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-1",
                "Widget layout is off", "ABC", List.of(), "https://tracker/ABC-1")), TokenUsage.NONE));

        launcher.launch(new LaunchRequest("ABC-1", "demo", "plan", null, null, "start with tests only"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().instructions()).contains("start with tests only");
    }

    @Test
    void carriesTheModeTheHumanAskedForThroughToTheAgent() {
        oneProject("demo");
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-1",
                "Widget layout is off", "ABC", List.of(), "https://tracker/ABC-1")), TokenUsage.NONE));

        launcher.launch(new LaunchRequest("ABC-1", "demo", "plan", null, null, "start with tests only"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().mode()).isEqualTo("plan");
    }

    @Test
    void carriesTheHumansBranchStrategyThroughToTheWorktreeCut() {
        oneProject("demo");
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-1",
                "Widget layout is off", "ABC", List.of(), "https://tracker/ABC-1")), TokenUsage.NONE));

        launcher.launch(new LaunchRequest("ABC-1", "demo", null, "recreate", null, null));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().branchStrategy()).isEqualTo("recreate");
    }

    @Test
    void carriesTheHumansBaseBranchThroughToTheWorktreeCut() {
        oneProject("demo");
        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-1",
                "Widget layout is off", "ABC", List.of(), "https://tracker/ABC-1")), TokenUsage.NONE));

        launcher.launch(new LaunchRequest("ABC-1", "demo", null, null, "feature/parent", null));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().baseBranch()).isEqualTo("feature/parent");
    }

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "ABC-1", "Some ticket title", "ABC",
                List.of("area-x", "no-test", "backend"), null);

        List<String> matches = TaskLauncher.projectsMatching(facts,
                Map.of("group-a", List.of("backend"), "group-b", List.of("frontend")));

        assertThat(matches).containsExactly("group-a");
    }

    @Test
    void createsOneTaskAcrossEveryProjectNamedInTheSameToken() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig("/api", "origin/main", "dev", List.of()),
                "web", new ProjectConfig("/web", "origin/main", "dev", List.of()))));

        when(tickets.read("ABC-1")).thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-1",
                "Widget layout is off", "ABC", List.of(), "https://tracker/ABC-1")), TokenUsage.NONE));

        launcher.launch(new LaunchRequest("ABC-1", "web,api", null, null, null, null).normalized());

        ArgumentCaptor<NewTask> created = ArgumentCaptor.captor();
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().projectKeys()).containsExactly("web", "api");
        assertThat(created.getValue().projectKey()).isEqualTo("web");
    }

    @Test
    void refusesTheLaunchWhenOneOfTheNamedProjectsIsNotConfigured() {
        oneProject("api");

        assertThatThrownBy(() -> launcher.launch(
                new LaunchRequest("ABC-1", "api,typo", null, null, null, null).normalized()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown project [typo]");

        verifyNoInteractions(provisioning);
    }

    private void oneProject(String key) {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of(key, new ProjectConfig("/p", "origin/main", "dev", List.of()))));
    }
}
