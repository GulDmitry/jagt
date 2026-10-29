package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.command.CommandReference;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardApiControllerTest {

    private record Declared(String id, String hint, boolean report, int rank) implements GlobalCommand {
        @Override
        public String run(String tail) {
            return tail.isBlank() ? id + " report" : id + " report about " + tail;
        }
    }

    private final TaskViews taskViews = mock(TaskViews.class);
    private final BoardApiController api = new BoardApiController(taskViews,
            mock(TaskEventStream.class), new GlobalCommands(List.of(
                    new Declared("stats", "what the calls cost", true, 100),
                    new Declared("do", "start a task", false, 40))),
            new HeaderState(new dev.jagt.orchestrator.job.Jobs(List.of(), org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class), Clock.systemUTC()), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()));

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
    void runsACommandThatIsNotAReportWithWhatWasTypedAfterIt() {
        assertThat(api.execute("do", "a1").message()).isEqualTo("do report about a1");
    }

    @Test
    void refusesToRunAReportAsACommand() {
        assertThatThrownBy(() -> api.execute("stats", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No command 'stats' to run");
    }

    @Test
    void reportsTheProjectsAlongsideTheTasks() {
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new AutoReviewCadence(false, Duration.ofHours(24), 10, 60, 240),
                List.of("demo"), null, "off"));

        var board = api.tasks();

        assertThat(board.projects()).containsExactly("demo");
    }

    @Test
    void tellsTheBoardWhenTheAssistantIsUnavailable() {
        var health = org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class);
        var outage = new dev.jagt.orchestrator.port.AssistantHealth.Outage("529 overloaded", 5_000L);
        when(health.outage()).thenReturn(java.util.Optional.of(outage));
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new AutoReviewCadence(false, Duration.ofHours(24), 10, 60, 240), List.of(), null, "off"));
        BoardApiController board = new BoardApiController(taskViews, mock(TaskEventStream.class),
                new GlobalCommands(List.of()), new HeaderState(new Jobs(List.of(), org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class), Clock.systemUTC()),
                health, Clock.systemUTC()));

        assertThat(board.tasks().assistant()).isEqualTo(outage);
    }

    @Test
    void servesTheStatusOfEveryJobTheBackendRuns() {
        Job poll = mock(Job.class);
        when(poll.id()).thenReturn("poll-reviews");
        BoardApiController board = new BoardApiController(taskViews, mock(TaskEventStream.class),
                new GlobalCommands(List.of()), new HeaderState(new Jobs(List.of(poll), org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class), Clock.systemUTC()), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()));

        assertThat(board.jobs()).extracting(Jobs.Status::id).containsExactly("poll-reviews");
    }

    @Test
    void handsTheBrowserTheStreamTheEventSourceOpened() {
        TaskEventStream stream = mock(TaskEventStream.class);
        SseEmitter browser = new SseEmitter(0L);
        when(stream.open()).thenReturn(browser);
        BoardApiController board = new BoardApiController(taskViews, stream, new GlobalCommands(List.of()),
                new HeaderState(new Jobs(List.of(), org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class), Clock.systemUTC()), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()));

        assertThat(board.events()).isSameAs(browser);
    }

    @Test
    void saysWhetherTheUnattendedPollRunsAtAllEvenWithNoTasks() {
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new AutoReviewCadence(true, Duration.ofHours(24), 10, 60, 240),
                List.of(), null, "off"));

        var board = api.tasks();

        assertThat(board.autoReviewEnabled()).isTrue();
        assertThat(board.autoReview()).isEqualTo("auto-review on");
    }

    @Test
    void offersOnACardTheReportThatDeclaresItselfForThatTask() {
        GlobalCommand drafts = new GlobalCommand() {
            public String id() { return "replies"; }
            public String hint() { return "the drafted answers"; }
            public int rank() { return 20; }
            public boolean report() { return true; }
            public boolean offeredOn(TaskView task) { return task.draftedReplies(); }
            public String run(String tail) { return ""; }
        };
        BoardApiController board = new BoardApiController(taskViews, mock(TaskEventStream.class),
                new GlobalCommands(List.of(drafts)), new HeaderState(new dev.jagt.orchestrator.job.Jobs(List.of(), org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class), Clock.systemUTC()), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()));
        TaskView drafted = TaskView.of("ABC-1", TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build(),
                true, null, Map.of());
        TaskView quiet = TaskView.of("ABC-2", TaskState.builder("demo", "/wt2", TaskStatus.REVIEW_PENDING).build(),
                false, null, Map.of());
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(drafted, quiet),
                new AutoReviewCadence(false, Duration.ofHours(24), 10, 60, 240), List.of("demo"), null, "off"));

        var offers = board.tasks().offers();

        assertThat(offers).containsExactly(Map.entry("ABC-1", List.of("replies")));
    }
}
