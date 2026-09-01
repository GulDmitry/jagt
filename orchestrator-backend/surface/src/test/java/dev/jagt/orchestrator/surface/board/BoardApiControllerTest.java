package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.command.CommandReference;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.TaskViews;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardApiControllerTest {

    private record Declared(String id, String hint, boolean report) implements GlobalCommand {
        @Override
        public String run(String tail) {
            return tail.isBlank() ? id + " report" : id + " report about " + tail;
        }
    }

    private final TaskViews taskViews = mock(TaskViews.class);
    private final BoardApiController api = new BoardApiController(taskViews,
            mock(TaskEventStream.class), new GlobalCommands(List.of(
                    new Declared("stats", "what the calls cost", true),
                    new Declared("do", "start a task", false))),
            new dev.jagt.orchestrator.job.Jobs(List.of()));

    @Test
    void servesEveryVerbThePaletteMustBeAbleToCompleteAndValidate() {
        var ids = api.commands().stream().map(CommandReference.Verb::id).toList();

        assertThat(ids).contains("ship", "sweep", "ide", "diff", "deploy", "revert", "respawn", "done", "focus",
                "do", "stats");
    }

    @Test
    void saysWhichVerbsAreNothingWithoutATaskToApplyThemTo() {
        assertThat(api.commands().stream().filter(CommandReference.Verb::takesTask)
                .map(CommandReference.Verb::id)).contains("ship").doesNotContain("do", "stats");
    }

    @Test
    void offersTheEverydayVerbsBeforeTheRareOnes() {
        var ids = api.commands().stream().map(CommandReference.Verb::id).toList();

        assertThat(ids).startsWith("sweep", "ship", "do");
        assertThat(ids.indexOf("ship")).isLessThan(ids.indexOf("focus"));
        assertThat(ids.indexOf("deploy")).isLessThan(ids.indexOf("done"));
    }

    @Test
    void servesAReportUnderTheIdOfTheCommandThatProducesIt() {
        assertThat(api.report("stats", null)).isEqualTo("stats report");
    }

    @Test
    void passesWhatTheBoardAsksAboutToTheCommandItself() {
        assertThat(api.report("stats", "a1")).isEqualTo("stats report about a1");
    }

    @Test
    void refusesToRunACommandThatIsNotAReport() {
        assertThatThrownBy(() -> api.report("do", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No report 'do'");
    }

    @Test
    void reportsTheProjectsAlongsideTheTasks() {
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new AutoReviewCadence(false, Duration.ofHours(24), 10, 60),
                List.of("demo")));

        var board = api.tasks();

        assertThat(board.projects()).containsExactly("demo");
    }

    @Test
    void saysWhetherTheUnattendedPollRunsAtAllEvenWithNoTasks() {
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new AutoReviewCadence(true, Duration.ofHours(24), 10, 60),
                List.of()));

        var board = api.tasks();

        assertThat(board.autoReviewEnabled()).isTrue();
        assertThat(board.autoReview()).isEqualTo("auto-review on");
    }
}
