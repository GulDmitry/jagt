package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DashboardRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterShellTest {

    @Test
    void matchesTheProjectWhoseLabelIsAmongTheTicketLabels() {
        TicketFacts facts = new TicketFacts(true, "ABC-1", "Some ticket title", "ABC",
                List.of("area-x", "no-test", "backend"), null);
        Map<String, List<String>> projectLabels = Map.of("group-a", List.of("backend"), "group-b", List.of("frontend"));

        List<String> matches = MasterShell.projectsMatching(facts, projectLabels);

        assertThat(matches).containsExactly("group-a");
    }

    @Test
    void namesTheTaskByTheAssistantsCanonicalKeyWhenGivenAUrl() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MasterAssistant assistant = mock(MasterAssistant.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(assistant.readTicket("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(Optional.of(new TicketFacts(true, "ABC-123", "Some title", "ABC", List.of(),
                        "https://tracker.example.com/browse/ABC-123")));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), config, assistant,
                mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "https://tracker.example.com/browse/ABC-123", "group-a"));

        verify(tools).initializeTask(eq("ABC-123"), eq("group-a"), anyString(), isNull(), isNull(),
                eq("Some title"), eq("https://tracker.example.com/browse/ABC-123"));
    }

    @Test
    void resumeCarriesTheMrTitleIntoTheTask() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MasterAssistant assistant = mock(MasterAssistant.class);
        when(assistant.readMergeRequest("https://host/mr/425"))
                .thenReturn(Optional.of(new MergeRequestFacts(true, "PROJ-1", "group/proj", "PROJ-1 Excel export")));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), mock(ConfigService.class), assistant,
                mock(ConfigurableApplicationContext.class));

        shell.resumeTask(List.of("resume", "https://host/mr/425"));

        verify(tools).resumeTask("PROJ-1", "https://host/mr/425", "PROJ-1 Excel export");
    }

    @Test
    void treatsFreeTextAfterPlanAsNotesNotAProject() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "sng", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "sobrado", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(DashboardRenderer.class),
                config, mock(MasterAssistant.class), mock(ConfigurableApplicationContext.class));

        MasterShell.DoArgs args = shell.parseDoArgs(List.of("do", "ABC-2099", "plan", "давай", "разберём", "алгоритм"));

        assertThat(args.project()).isNull();
        assertThat(args.mode()).isEqualTo("plan");
        assertThat(args.notes()).isEqualTo("давай разберём алгоритм");
    }

    @Test
    void relaysInlineNotesToTheAgentAfterConsumingLeadingPlanAndProject() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "sng", new ProjectConfig("/a", "origin/main", "dev", List.of()))));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), config,
                mock(MasterAssistant.class), mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "ABC-1", "plan", "sng", "start", "with", "tests", "only"));

        verify(tools).initializeTask(eq("ABC-1"), eq("sng"), contains("start with tests only"),
                eq("plan"), isNull(), isNull(), isNull());
    }

    @Test
    void exitClosesTheSpringContextInsteadOfLeavingItToTheShutdownHook() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(DashboardRenderer.class),
                mock(ConfigService.class), mock(MasterAssistant.class), context);

        shell.stopBackend();

        verify(context).close();
    }

    @Test
    void tabCompletesAUniqueCommand() {
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(DashboardRenderer.class),
                mock(ConfigService.class), mock(MasterAssistant.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("sh");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship ");
    }

    @Test
    void tabCompletesATaskAliasFromTheLiveTasks() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        when(tools.taskChoices()).thenReturn(List.of(new OrchestratorTools.TaskChoice("p1", "PAN-2536", "Excel")));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), mock(ConfigService.class),
                mock(MasterAssistant.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p1");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship p1 ");
    }

    @Test
    void tabListsAmbiguousTasksWithTheirTitlesInsteadOfCompleting() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        when(tools.taskChoices()).thenReturn(List.of(
                new OrchestratorTools.TaskChoice("p1", "PAN-2536", "Excel export flag"),
                new OrchestratorTools.TaskChoice("p2", "PAN-2540", "Login rate limit")));
        MasterShell shell = new MasterShell(tools, mock(DashboardRenderer.class), mock(ConfigService.class),
                mock(MasterAssistant.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p");
        List<String> log = new ArrayList<>();

        shell.completeInput(editor, log);

        assertThat(editor.text()).isEqualTo("ship p");   // ambiguous → unchanged, options listed instead
        assertThat(log).anyMatch(l -> l.contains("PAN-2536") && l.contains("Excel export flag"));
        assertThat(log).anyMatch(l -> l.contains("PAN-2540") && l.contains("Login rate limit"));
    }

    @Test
    void appendsTheDashboardAfterANonBlankResult() {
        assertThat(MasterShell.withDashboard("shipped p1", "DASH")).isEqualTo("shipped p1\n\nDASH");
    }

    @Test
    void showsTheDashboardAloneForABlankResult() {
        assertThat(MasterShell.withDashboard("", "DASH")).isEqualTo("DASH");
    }
}
