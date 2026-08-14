package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StateViews;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MasterShellTest {
    @Test
    void treatsFreeTextAfterPlanAsNotesNotAProject() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()),
                "sobrado", new ProjectConfig("/b", "origin/stage", "dev", List.of()))));
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                config, mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));

        LaunchRequest args = shell.parseDoArgs(List.of("do", "ABC-2099", "plan", "walk", "me", "through", "it"));

        assertThat(args.project()).isNull();
        assertThat(args.mode()).isEqualTo("plan");
        assertThat(args.notes()).isEqualTo("walk me through it");
    }

    @Test
    void readsTheBaseBranchAfterFromAndKeepsTheRestAsNotes() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "demo", new ProjectConfig("/a", "origin/main", "dev", List.of()))));
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                config, mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));

        LaunchRequest args = shell.parseDoArgs(
                List.of("do", "ABC-1", "from", "feature/parent", "demo", "keep the API stable"));

        assertThat(args.baseBranch()).isEqualTo("feature/parent");
        assertThat(args.project()).isEqualTo("demo");
        assertThat(args.notes()).isEqualTo("keep the API stable");
    }

    /**
     * A review request names its own source and target branch, so a ticket typed beside its URL can only
     * contradict it — and the task that came out would be a branch the request does not track, which the next
     * `ship` pushes while the request keeps waiting on the other one.
     */
    @Test
    void refusesAResumeThatTriesToNameTheTaskBesideTheRequestUrl() {
        TaskLauncher launcher = mock(TaskLauncher.class);
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                mock(ConfigService.class), mock(CommandService.class), launcher, mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));

        assertThatThrownBy(() -> shell.resumeTask(List.of("resume", "https://host/mr/42", "ABC-9")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carries its own branches");
        verifyNoInteractions(launcher);
    }

    @Test
    void refusesFromWithoutABranchInsteadOfSwallowingTheNextWord() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                config, mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));

        assertThatThrownBy(() -> shell.parseDoArgs(List.of("do", "ABC-1", "from")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("`from` needs the branch");
    }

    @Test
    void exitClosesTheSpringContextInsteadOfLeavingItToTheShutdownHook() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                mock(ConfigService.class), mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), context);

        shell.stopBackend();

        verify(context).close();
    }

    @Test
    void tabCompletesAUniqueCommand() {
        MasterShell shell = new MasterShell(mock(OrchestratorTools.class), mock(StateViews.class),
                mock(ConfigService.class), mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("sh");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship ");
    }

    @Test
    void tabCompletesATaskAliasFromTheLiveTasks() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        when(tools.taskChoices()).thenReturn(List.of(new OrchestratorTools.TaskChoice("p1", "ABC-2536", "Excel")));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), mock(ConfigService.class),
                mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p1");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship p1 ");
    }

    @Test
    void tabListsAmbiguousTasksWithTheirTitlesInsteadOfCompleting() {
        OrchestratorTools tools = mock(OrchestratorTools.class);
        when(tools.taskChoices()).thenReturn(List.of(
                new OrchestratorTools.TaskChoice("p1", "ABC-2536", "Excel export flag"),
                new OrchestratorTools.TaskChoice("p2", "ABC-2540", "Login rate limit")));
        MasterShell shell = new MasterShell(tools, mock(StateViews.class), mock(ConfigService.class),
                mock(CommandService.class), mock(TaskLauncher.class), mock(StateService.class),
                mock(NaturalLanguageDispatch.class), mock(ConfigurableApplicationContext.class));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p");
        List<String> log = new ArrayList<>();

        shell.completeInput(editor, log);

        assertThat(editor.text()).isEqualTo("ship p");   // ambiguous → unchanged, options listed instead
        assertThat(log).anyMatch(l -> l.contains("ABC-2536") && l.contains("Excel export flag"));
        assertThat(log).anyMatch(l -> l.contains("ABC-2540") && l.contains("Login rate limit"));
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
