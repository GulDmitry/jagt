package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.CommandReference;
import dev.jagt.orchestrator.service.GlobalCommand;
import dev.jagt.orchestrator.service.GlobalCommands;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardApiControllerTest {

    private record Declared(String id, String hint, boolean report, boolean consoleOnly) implements GlobalCommand {
        @Override
        public String run(String tail) {
            return id + " report";
        }
    }

    private final TaskViews taskViews = mock(TaskViews.class);
    private final UsageTracker usageTracker = mock(UsageTracker.class);
    private final BoardApiController api = new BoardApiController(taskViews, usageTracker,
            mock(TaskEventStream.class), new GlobalCommands(List.of(
                    new Declared("stats", "what the calls cost", true, false),
                    new Declared("do", "start a task", false, false),
                    new Declared("status", "show the dashboard", false, true))));

    /**
     * The palette completes and validates against THIS list, so a verb the console accepts and this omits is a
     * capability the board cannot express — the parity bug in miniature.
     */
    @Test
    void servesEveryVerbThePaletteMustBeAbleToCompleteAndValidate() {
        var ids = api.commands().stream().map(CommandReference.Verb::id).toList();

        assertThat(ids).contains("ship", "sweep", "ide", "diff", "deploy", "revert", "respawn", "done", "focus",
                "do", "stats");
        // Whether a verb needs a task is what decides if "ship" alone is a mistake or a command.
        assertThat(api.commands().stream().filter(CommandReference.Verb::takesTask)
                .map(CommandReference.Verb::id)).contains("ship").doesNotContain("do", "stats");
    }

    /** A verb that only means something in a terminal is not a button the board can grow. */
    @Test
    void keepsATerminalOnlyVerbOutOfWhatTheBoardIsToldAbout() {
        assertThat(api.commands()).extracting(CommandReference.Verb::id).doesNotContain("status");
    }

    @Test
    void offersTheEverydayVerbsBeforeTheRareOnes() {
        var ids = api.commands().stream().map(CommandReference.Verb::id).toList();

        assertThat(ids).startsWith("sweep", "ship", "do");
        assertThat(ids.indexOf("ship")).isLessThan(ids.indexOf("focus"));
        assertThat(ids.indexOf("deploy")).isLessThan(ids.indexOf("done"));
    }

    /** One address for every report, so declaring another one needs no endpoint. */
    @Test
    void servesAReportUnderTheIdOfTheCommandThatProducesIt() {
        assertThat(api.report("stats")).isEqualTo("stats report");
    }

    /** A GET must not be able to start a task, whatever id is put in the URL. */
    @Test
    void refusesToRunACommandThatIsNotAReport() {
        assertThatThrownBy(() -> api.report("do")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No report 'do'");
    }

    @Test
    void refusesAReportOnlyTheConsoleCouldShow() {
        assertThatThrownBy(() -> api.report("status")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsTheSessionSpendAndTheProjectsAlongsideTheTasks() {
        when(usageTracker.session()).thenReturn(dev.jagt.orchestrator.task.TokenUsage.ofCall(1000, 0, 50, 0.1));
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new dev.jagt.orchestrator.service.AutoReviewCadence(false, java.time.Duration.ofHours(24), 10, 60),
                List.of("demo")));

        var board = api.tasks();

        assertThat(board.spend().calls()).isEqualTo(1);
        assertThat(board.spend().tokens()).isEqualTo(1050);
        assertThat(board.projects()).containsExactly("demo");
    }

    /** A board with nothing out for review still has to say whether anything would be polled. */
    @Test
    void saysWhetherTheUnattendedPollRunsAtAllEvenWithNoTasks() {
        when(usageTracker.session()).thenReturn(dev.jagt.orchestrator.task.TokenUsage.NONE);
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new dev.jagt.orchestrator.service.AutoReviewCadence(true, java.time.Duration.ofHours(24), 10, 60),
                List.of()));

        var board = api.tasks();

        assertThat(board.autoReviewEnabled()).isTrue();
        assertThat(board.autoReview()).isEqualTo("auto-review on");
    }
}
