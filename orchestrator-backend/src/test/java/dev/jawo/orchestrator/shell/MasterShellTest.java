package dev.jawo.orchestrator.shell;

import dev.jawo.orchestrator.assistant.MasterAssistant;
import dev.jawo.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jawo.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jawo.orchestrator.mcp.OrchestratorTools;
import dev.jawo.orchestrator.model.ProjectConfig;
import dev.jawo.orchestrator.service.ConfigService;
import dev.jawo.orchestrator.service.DashboardRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterShellTest {

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "ABC-1", "Some ticket title", "ABC", List.of("area-x", "no-test", "backend"));
        Map<String, List<String>> projectLabels = Map.of("group-a", List.of("backend"), "group-b", List.of("frontend"));

        List<String> matches = MasterShell.projectsMatching(facts, projectLabels);

        assertThat(matches).containsExactly("group-a");
    }

    @Test
    void namesTheTaskByTheAssistantsCanonicalKeyWhenGivenAUrl() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MasterAssistant assistant = mock(MasterAssistant.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(
                Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of())),
                null, null, null, null, null, null, null, null, null));
        when(assistant.readTicket("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(Optional.of(new TicketFacts(true, "ABC-123", "Some title", "ABC", List.of())));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), config, assistant);

        shell.doTask(List.of("do", "https://tracker.example.com/browse/ABC-123", "group-a"));

        verify(tools).initializeTask(eq("ABC-123"), eq("group-a"), anyString(), isNull(), isNull(), eq("Some title"));
    }

    @Test
    void resumeCarriesTheMrTitleIntoTheTask() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MasterAssistant assistant = mock(MasterAssistant.class);
        when(assistant.readMergeRequest("https://host/mr/425"))
                .thenReturn(Optional.of(new MergeRequestFacts(true, "PROJ-1", "group/proj", "PROJ-1 Excel export")));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), mock(ConfigService.class), assistant);

        shell.resumeTask(List.of("resume", "https://host/mr/425"));

        verify(tools).resumeTask("PROJ-1", "https://host/mr/425", "PROJ-1 Excel export");
    }

    @Test
    void pinnedDashboardKeepsCommandOutputToTheResultOnly() {
        assertThat(MasterShell.withDashboard("shipped p1", true, "DASH")).isEqualTo("shipped p1");
    }

    @Test
    void inlineDashboardAppendsItAfterANonBlankResult() {
        assertThat(MasterShell.withDashboard("shipped p1", false, "DASH")).isEqualTo("shipped p1\n\nDASH");
    }

    @Test
    void inlineDashboardIsShownAloneForABlankResult() {
        assertThat(MasterShell.withDashboard("", false, "DASH")).isEqualTo("DASH");
    }
}
