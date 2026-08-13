package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.assistant.MasterAssistant.MergeRequestFacts;
import dev.jagt.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.model.TokenUsage;
import dev.jagt.orchestrator.service.MeteredAssistant;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.ReviewSweepService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        MeteredAssistant assistant = mock(MeteredAssistant.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(assistant.readTicket("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-123", "Some title", "ABC",
                        List.of(), "https://tracker.example.com/browse/ABC-123")), TokenUsage.NONE));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), config, assistant,
                mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "https://tracker.example.com/browse/ABC-123", "group-a"));

        verify(tools).initializeTask(eq("ABC-123"), eq("group-a"), anyString(), isNull(), isNull(),
                eq("Some title"), eq("https://tracker.example.com/browse/ABC-123"));
    }

    @Test
    void chargesTheTicketReadToTheTaskItJustNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MeteredAssistant assistant = mock(MeteredAssistant.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(assistant.readTicket("https://tracker.example.com/browse/ABC-123"))
                .thenReturn(new Answer<>(Optional.of(new TicketFacts(true, "ABC-123", "Some title", "ABC",
                        List.of(), "https://tracker.example.com/browse/ABC-123")), spent));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), config, assistant,
                mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "https://tracker.example.com/browse/ABC-123", "group-a"));

        // The read happens BEFORE the task exists, so it can only be charged after initializeTask created it.
        var order = org.mockito.Mockito.inOrder(tools, assistant);
        order.verify(tools).initializeTask(eq("ABC-123"), eq("group-a"), anyString(), isNull(), isNull(),
                eq("Some title"), eq("https://tracker.example.com/browse/ABC-123"));
        order.verify(assistant).chargeTask("ABC-123", spent);
    }

    @Test
    void chargesAFailedTicketReadToTheTaskTheBareKeyStillCreated() {
        TokenUsage spent = TokenUsage.ofCall(38_000, 0, 60, 0.41);
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MeteredAssistant assistant = mock(MeteredAssistant.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        // The read burned tokens and came back unusable; a bare key needs no read, so the task is created anyway.
        when(assistant.readTicket("ABC-42")).thenReturn(new Answer<>(Optional.empty(), spent));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), config, assistant,
                mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "ABC-42"));

        verify(tools).initializeTask(eq("ABC-42"), eq("group-a"), anyString(), isNull(), isNull(),
                isNull(), isNull());
        verify(assistant).chargeTask("ABC-42", spent);
    }

    @Test
    void resumeCarriesTheMrTitleIntoTheTask() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MeteredAssistant assistant = mock(MeteredAssistant.class);
        when(assistant.readMergeRequest("https://host/mr/425")).thenReturn(new Answer<>(
                Optional.of(new MergeRequestFacts(true, "PROJ-1", "group/proj", "PROJ-1 Excel export")),
                TokenUsage.NONE));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), mock(ConfigService.class), assistant,
                mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        shell.resumeTask(List.of("resume", "https://host/mr/425"));

        verify(tools).resumeTask("PROJ-1", "https://host/mr/425", "PROJ-1 Excel export");
    }

    @Test
    void treatsFreeTextAfterPlanAsNotesNotAProject() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "sng", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "sobrado", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                config, mock(MeteredAssistant.class), mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

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
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), config,
                mock(MeteredAssistant.class), mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "ABC-1", "plan", "sng", "start", "with", "tests", "only"));

        verify(tools).initializeTask(eq("ABC-1"), eq("sng"), contains("start with tests only"),
                eq("plan"), isNull(), isNull(), isNull());
    }

    @Test
    void warnsAboutALeftoverBranchWithoutReadingTheTicket() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        MeteredAssistant assistant = mock(MeteredAssistant.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("group-a", new ProjectConfig("/p", "origin/main", "dev", List.of()))));
        when(tools.existingBranchProject("ABC-9", null)).thenReturn("group-a");
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), config, assistant,
                mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        String out = shell.doTask(List.of("do", "ABC-9"));

        assertThat(out).contains("already exists in group-a", "do ABC-9 recreate", "do ABC-9 resume");
        verifyNoInteractions(assistant);
        verify(tools, never()).initializeTask(anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void threadsTheChosenBranchStrategyIntoInitialize() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("sng", new ProjectConfig("/a", "origin/main", "dev", List.of()))));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), config,
                mock(MeteredAssistant.class), mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));

        shell.doTask(List.of("do", "ABC-1", "sng", "recreate"));

        verify(tools).initializeTask(eq("ABC-1"), eq("sng"), anyString(), isNull(), eq("recreate"),
                isNull(), isNull());
    }

    @Test
    void exitClosesTheSpringContextInsteadOfLeavingItToTheShutdownHook() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                mock(ConfigService.class), mock(MeteredAssistant.class), mock(ReviewSweepService.class), context);

        shell.stopBackend();

        verify(context).close();
    }

    @Test
    void tabCompletesAUniqueCommand() {
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                mock(ConfigService.class), mock(MeteredAssistant.class), mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("sh");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship ");
    }

    @Test
    void tabCompletesATaskAliasFromTheLiveTasks() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        when(tools.taskChoices()).thenReturn(List.of(new OrchestratorTools.TaskChoice("p1", "PAN-2536", "Excel")));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), mock(ConfigService.class),
                mock(MeteredAssistant.class), mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));
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
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), mock(ConfigService.class),
                mock(MeteredAssistant.class), mock(ReviewSweepService.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p");
        List<String> log = new ArrayList<>();

        shell.completeInput(editor, log);

        assertThat(editor.text()).isEqualTo("ship p");   // ambiguous → unchanged, options listed instead
        assertThat(log).anyMatch(l -> l.contains("PAN-2536") && l.contains("Excel export flag"));
        assertThat(log).anyMatch(l -> l.contains("PAN-2540") && l.contains("Login rate limit"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prune ABC-1", "prune all extra", "prune ALL", "prune -f", "prune yes"})
    void refusesAnythingButABarePruneOrPruneAllRatherThanGuessing(String line) {
        // This guard is all that stands between a typo and a bulk delete across every project, so it must
        // never read an unknown token as "yes, delete" — and `prune ABC-1` (a per-task prune that does not
        // exist) must not silently prune everything.
        assertThatThrownBy(() -> MasterShell.pruneDeletes(List.of(line.split(" "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage: prune [all]");
    }

    @Test
    void listsOnABarePruneAndDeletesOnlyOnPruneAll() {
        assertThat(MasterShell.pruneDeletes(List.of("prune"))).isFalse();
        assertThat(MasterShell.pruneDeletes(List.of("prune", "all"))).isTrue();
    }

    @Test
    void appendsTheDashboardAfterANonBlankResult() {
        assertThat(MasterShell.withDashboard("shipped p1", "DASH")).isEqualTo("shipped p1\n\nDASH");
    }

    @Test
    void showsTheDashboardAloneForABlankResult() {
        assertThat(MasterShell.withDashboard("", "DASH")).isEqualTo("DASH");
    }

    @Test
    void wrapsALongNextMoveLineSoItsClippedTailBecomesVisible() {
        String line = " ".repeat(20) + "→ your move: resolve the deploy conflict in the deploy worktree, "
                + "then `deploy` again";

        List<String> out = MasterShell.wrapHanging(line, 60);

        assertThat(out).hasSizeGreaterThan(1);
        assertThat(out).allSatisfy(seg -> assertThat(seg.length()).isLessThanOrEqualTo(60));
        // The tail that `fit()` used to clip is now spread across continuation lines — nothing is lost.
        String rejoined = out.stream().map(String::strip).reduce((a, b) -> a + " " + b).orElse("");
        assertThat(rejoined).contains("then `deploy` again");
    }

    @Test
    void hangsWrappedContinuationsUnderTheTextPastTheMarker() {
        String line = " ".repeat(20) + "→ " + "x".repeat(80);

        List<String> out = MasterShell.wrapHanging(line, 40);

        assertThat(out.get(0)).contains("→");
        assertThat(out.get(1)).startsWith(" ".repeat(22)).doesNotContain("→");   // 20 indent + "→ "
    }

    @Test
    void hangingIndentCountsLeadingSpacesPlusTheMarker() {
        assertThat(MasterShell.hangingIndent(" ".repeat(20) + "→ your move")).isEqualTo(22);
        assertThat(MasterShell.hangingIndent(" ".repeat(20) + "└ http://x")).isEqualTo(22);
        assertThat(MasterShell.hangingIndent("plain header, no indent")).isZero();
    }

    @Test
    void leavesADetailLineThatFitsUnwrapped() {
        String line = " ".repeat(20) + "└ http://mr/1";
        assertThat(MasterShell.wrapHanging(line, 80)).containsExactly(line);
    }
}
