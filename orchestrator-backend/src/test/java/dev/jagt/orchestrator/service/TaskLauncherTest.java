package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TokenUsage;
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
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final TaskResume resumes = mock(TaskResume.class);
    private final TicketTitleBackfill titles = mock(TicketTitleBackfill.class);
    private final TaskLauncher launcher = new TaskLauncher(provisioning, assistant, configService, resumes,
            titles);

    @BeforeEach
    void configIsReadable() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    @Test
    void namesTheTaskByTheCanonicalKeyTheAssistantReadWhenGivenAUrl() {
        oneProject("group-a");
        when(assistant.readTicket("https://tracker.example.com/browse/ABC-123"))
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

    @Test
    void chargesTheTicketReadToTheTaskItJustNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        oneProject("group-a");
        when(assistant.readTicket("https://tracker/ABC-123")).thenReturn(new Answer<>(
                Optional.of(new TicketFacts(true, "ABC-123", "t", "ABC", List.of(), "")), spent));

        launcher.launch(new LaunchRequest("https://tracker/ABC-123", "group-a", null, null, null, null));

        // The read happens BEFORE the task exists, so it can only be charged after initializeTask created it.
        var order = inOrder(provisioning, assistant);
        order.verify(provisioning).initializeTask(any());
        order.verify(assistant).chargeTask("ABC-123", spent);
    }

    @Test
    void chargesAFailedTicketReadToTheTaskTheBareKeyStillCreated() {
        TokenUsage spent = TokenUsage.ofCall(38_000, 0, 60, 0.41);
        oneProject("group-a");
        when(assistant.readTicket("ABC-42")).thenReturn(new Answer<>(Optional.empty(), spent));

        launcher.launch(LaunchRequest.of("ABC-42"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue()).extracting(NewTask::taskId, NewTask::projectKey)
                .containsExactly("ABC-42", "group-a");
        verify(assistant).chargeTask("ABC-42", spent);
    }

    @Test
    void warnsAboutALeftoverBranchWithoutSpendingATicketRead() {
        oneProject("group-a");
        when(provisioning.existingBranchProject("ABC-9", null)).thenReturn("group-a");

        String out = launcher.launch(LaunchRequest.of("ABC-9"));

        assertThat(out).contains("already exists in group-a", "do ABC-9 recreate", "do ABC-9 resume");
        verifyNoInteractions(assistant);
        verify(provisioning, never()).initializeTask(any());
    }

    @Test
    void relaysTheHumansNotesToTheAgentAlongsideTheTicket() {
        oneProject("demo");

        launcher.launch(new LaunchRequest("ABC-1", "demo", "plan", null, null, "start with tests only"));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().instructions()).contains("start with tests only");
        assertThat(created.getValue().mode()).isEqualTo("plan");
    }

    @Test
    void threadsTheChosenBranchStrategyIntoInitialize() {
        oneProject("demo");

        launcher.launch(new LaunchRequest("ABC-1", "demo", null, "recreate", null, null));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().branchStrategy()).isEqualTo("recreate");
    }

    @Test
    void threadsTheChosenBaseBranchIntoInitialize() {
        oneProject("demo");

        launcher.launch(new LaunchRequest("ABC-1", "demo", null, null, "feature/parent", null));

        ArgumentCaptor<NewTask> created = ArgumentCaptor.forClass(NewTask.class);
        verify(provisioning).initializeTask(created.capture());
        assertThat(created.getValue().baseBranch()).isEqualTo("feature/parent");
    }

    @Test
    void carriesTheReviewRequestTitleAndItsTargetBranchIntoAResumedTask() {
        when(assistant.readMergeRequest("https://host/mr/425")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, "PROJ-1", "release/2", "group/proj",
                        "PROJ-1 Excel export")),
                TokenUsage.NONE));

        launcher.resume("https://host/mr/425");

        verify(resumes).resume("PROJ-1", "https://host/mr/425", "PROJ-1 Excel export", "release/2");
    }

    /**
     * Taking over someone else's request means taking over a branch named by someone else's convention, and a
     * jagt task IS its branch (also a directory and a tmux window). Naming the branch and the way out beats
     * the generic id check reporting a regex against a name the human never typed.
     */
    @Test
    void explainsWhyASlashedSourceBranchCannotBecomeATask() {
        when(assistant.readMergeRequest("https://host/mr/426")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, "feature/widget-layout", "main", "group/proj",
                        "Widget layout is off")),
                TokenUsage.NONE));

        String result = launcher.resume("https://host/mr/426");

        assertThat(result).contains("feature/widget-layout").contains("do <ticket> from");
        verifyNoInteractions(provisioning);
    }

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "ABC-1", "Some ticket title", "ABC",
                List.of("area-x", "no-test", "backend"), null);

        List<String> matches = TaskLauncher.projectsMatching(facts,
                Map.of("group-a", List.of("backend"), "group-b", List.of("frontend")));

        assertThat(matches).containsExactly("group-a");
    }

    private void oneProject(String key) {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of(key, new ProjectConfig("/p", "origin/main", "dev", List.of()))));
    }

    @Test
    void asksForTheTitleAfterALaunchThatSkippedTheTicketRead() {
        oneProject("group-a");

        launcher.launch(new LaunchRequest("ABC-7", "group-a", null, null, null, null));

        verify(provisioning).initializeTask(any());
        verify(titles).of("ABC-7");
    }
}
