package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskLauncherTest {

    private final OrchestratorTools tools = mock(OrchestratorTools.class);
    private final MeteredAssistant assistant = mock(MeteredAssistant.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final StateService stateService = mock(StateService.class);
    private final TaskLauncher launcher = new TaskLauncher(tools, assistant, configService, stateService);

    @BeforeEach
    void noTasksYet() {
        when(stateService.tasks()).thenReturn(Map.of());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    @Test
    void namesTheTaskByTheCanonicalKeyTheAssistantReadWhenGivenAUrl() {
        oneProject("group-a");
        when(assistant.readTicket("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-123", "Some title", "ABC",
                        List.of(), "https://tracker.example.com/browse/ABC-123")), TokenUsage.NONE));

        launcher.launch("https://tracker.example.com/browse/ABC-123", "group-a", null, null, null);

        verify(tools).initializeTask(eq("ABC-123"), eq("group-a"), anyString(), isNull(), isNull(),
                eq("Some title"), eq("https://tracker.example.com/browse/ABC-123"));
    }

    @Test
    void chargesTheTicketReadToTheTaskItJustNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        oneProject("group-a");
        when(assistant.readTicket("https://tracker/ABC-123")).thenReturn(new Answer<>(
                Optional.of(new TicketFacts(true, "ABC-123", "t", "ABC", List.of(), "")), spent));

        launcher.launch("https://tracker/ABC-123", "group-a", null, null, null);

        // The read happens BEFORE the task exists, so it can only be charged after initializeTask created it.
        var order = inOrder(tools, assistant);
        order.verify(tools).initializeTask(eq("ABC-123"), anyString(), anyString(), any(), any(), any(), any());
        order.verify(assistant).chargeTask("ABC-123", spent);
    }

    @Test
    void chargesAFailedTicketReadToTheTaskTheBareKeyStillCreated() {
        TokenUsage spent = TokenUsage.ofCall(38_000, 0, 60, 0.41);
        oneProject("group-a");
        when(assistant.readTicket("ABC-42")).thenReturn(new Answer<>(Optional.empty(), spent));

        launcher.launch("ABC-42", null, null, null, null);

        verify(tools).initializeTask(eq("ABC-42"), eq("group-a"), anyString(), isNull(), isNull(), isNull(),
                isNull());
        verify(assistant).chargeTask("ABC-42", spent);
    }

    @Test
    void warnsAboutALeftoverBranchWithoutSpendingATicketRead() {
        oneProject("group-a");
        when(tools.existingBranchProject("ABC-9", null)).thenReturn("group-a");

        String out = launcher.launch("ABC-9", null, null, null, null);

        assertThat(out).contains("already exists in group-a", "do ABC-9 recreate", "do ABC-9 resume");
        verifyNoInteractions(assistant);
        verify(tools, never()).initializeTask(anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void relaysTheHumansNotesToTheAgentAlongsideTheTicket() {
        oneProject("sng");

        launcher.launch("ABC-1", "sng", "plan", null, "start with tests only");

        verify(tools).initializeTask(eq("ABC-1"), eq("sng"), contains("start with tests only"), eq("plan"),
                isNull(), isNull(), isNull());
    }

    @Test
    void threadsTheChosenBranchStrategyIntoInitialize() {
        oneProject("sng");

        launcher.launch("ABC-1", "sng", null, "recreate", null);

        verify(tools).initializeTask(eq("ABC-1"), eq("sng"), anyString(), isNull(), eq("recreate"), isNull(),
                isNull());
    }

    @Test
    void carriesTheReviewRequestTitleIntoAResumedTask() {
        when(assistant.readMergeRequest("https://host/mr/425")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, "PROJ-1", "group/proj", "PROJ-1 Excel export")),
                TokenUsage.NONE));

        launcher.resume("https://host/mr/425", null);

        verify(tools).resumeTask("PROJ-1", "https://host/mr/425", "PROJ-1 Excel export");
    }

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "ABC-1", "Some ticket title", "ABC",
                List.of("area-x", "no-test", "backend"), null);

        List<String> matches = TaskLauncher.projectsMatching(facts,
                Map.of("group-a", List.of("backend"), "group-b", List.of("frontend")));

        assertThat(matches).containsExactly("group-a");
    }

    /**
     * Reading the ticket is a paid model call, so a launch that the cap will refuse must be refused BEFORE it:
     * the enforcement point is in provisioning, but by then the money is gone.
     */
    @Test
    void refusesALaunchOverTheCapWithoutPayingForTheTicketRead() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of())))
                .withAgent(ConfigService.ConfigFile.AgentConfig.defaults().withMaxConcurrentTasks(1)));
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                dev.jagt.orchestrator.model.TaskState.builder("group-a", "/wt",
                        dev.jagt.orchestrator.model.TaskStatus.IN_PROGRESS).alias("a1").build()));

        assertThatThrownBy(() -> launcher.launch("https://tracker/ABC-9", "group-a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task slots are in use");
        verifyNoInteractions(assistant, tools);
    }

    private void oneProject(String key) {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of(key, new ProjectConfig("/p", "origin/main", "dev", List.of()))));
    }
}
