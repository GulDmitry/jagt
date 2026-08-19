package dev.jagt.orchestrator.surface.console;

import dev.jagt.orchestrator.task.TaskChoice;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.command.StateViews;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterShellTest {

    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    private final StateViews views = mock(StateViews.class);
    private final GrammarDispatch grammar = mock(GrammarDispatch.class);
    private final MasterShell shell = new MasterShell(views, mock(ConfigService.class),
            mock(StateService.class), grammar, context);

    @Test
    void exitClosesTheSpringContextInsteadOfLeavingItToTheShutdownHook() {
        shell.stopBackend();

        verify(context).close();
    }

    @Test
    void tabCompletesAUniqueCommand() {
        when(grammar.completions()).thenReturn(List.of("ship", "sweep", "revert", "review"));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("sh");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship ");
    }

    @Test
    void tabDoesNotTurnAnAmbiguousPrefixIntoTheSharedBranchWrite() {
        when(grammar.completions()).thenReturn(List.of("ship", "sweep", "revert", "review"));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("rev");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("rev");
    }

    @Test
    void tabCompletesTheTaskArgumentOfAVerbTypedByItsRetiredSpelling() {
        when(views.taskChoices()).thenReturn(List.of(new TaskChoice("p1", "ABC-2536", "Excel")));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("review p1");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("review p1 ");
    }

    @Test
    void tabCompletesTheTaskArgumentOfAVerbTypedWithCapitals() {
        when(views.taskChoices()).thenReturn(List.of(new TaskChoice("p1", "ABC-2536", "Excel")));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("Ship p1");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("Ship p1 ");
    }

    @Test
    void tabCompletesATaskAliasFromTheLiveTasks() {
        when(views.taskChoices()).thenReturn(List.of(new TaskChoice("p1", "ABC-2536", "Excel")));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p1");

        shell.completeInput(editor, new ArrayList<>());

        assertThat(editor.text()).isEqualTo("ship p1 ");
    }

    @Test
    void tabListsAmbiguousTasksWithTheirTitlesInsteadOfCompleting() {
        when(views.taskChoices()).thenReturn(List.of(new TaskChoice("p1", "ABC-2536", "Excel export flag"),
                new TaskChoice("p2", "ABC-2540", "Login rate limit")));
        MasterShell.LineEditor editor = new MasterShell.LineEditor();
        editor.setText("ship p");
        List<String> log = new ArrayList<>();

        shell.completeInput(editor, log);

        assertThat(editor.text()).isEqualTo("ship p");
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

    /** What {@code fit()} used to clip is spread across continuation lines instead, so nothing is lost. */
    @Test
    void wrapsALongNextMoveLineSoItsClippedTailBecomesVisible() {
        String line = " ".repeat(20) + "→ your move: resolve the deploy conflict in the deploy worktree, "
                + "then `deploy` again";

        List<String> out = MasterShell.wrapHanging(line, 60);

        assertThat(out).hasSizeGreaterThan(1);
        assertThat(out).allSatisfy(seg -> assertThat(seg.length()).isLessThanOrEqualTo(60));
        assertThat(String.join(" ", out.stream().map(String::strip).toList()))
                .contains("then `deploy` again");
    }

    @Test
    void hangsWrappedContinuationsUnderTheTextPastTheMarker() {
        String line = " ".repeat(20) + "→ " + "x".repeat(80);

        List<String> out = MasterShell.wrapHanging(line, 40);

        assertThat(out.get(0)).contains("→");
        assertThat(out.get(1)).startsWith(" ".repeat(22)).doesNotContain("→");
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
