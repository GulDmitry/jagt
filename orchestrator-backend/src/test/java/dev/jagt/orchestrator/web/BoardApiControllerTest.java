package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardApiControllerTest {

    private final TaskViews taskViews = mock(TaskViews.class);
    private final UsageTracker usageTracker = mock(UsageTracker.class);
    private final StateViews views = mock(StateViews.class);
    private final BoardApiController api = new BoardApiController(taskViews, usageTracker,
            mock(TaskEventStream.class), views);

    /**
     * The palette completes and validates against THIS list, so a verb the console accepts and this omits is a
     * capability the board cannot express — the parity bug in miniature.
     */
    @Test
    void servesEveryVerbThePaletteMustBeAbleToCompleteAndValidate() {
        var ids = api.commands().stream().map(dev.jagt.orchestrator.service.CommandReference.Verb::id).toList();

        assertThat(ids).contains("ship", "sweep", "ide", "diff", "deploy", "revert", "respawn", "done", "focus",
                "do", "resume", "stats", "help");
        // Whether a verb needs a task is what decides if "ship" alone is a mistake or a command.
        assertThat(api.commands().stream()
                .filter(dev.jagt.orchestrator.service.CommandReference.Verb::takesTask)
                .map(dev.jagt.orchestrator.service.CommandReference.Verb::id))
                .contains("ship").doesNotContain("do", "resume", "help");
    }

    @Test
    void offersTheEverydayVerbsBeforeTheRareOnes() {
        var ids = api.commands().stream().map(dev.jagt.orchestrator.service.CommandReference.Verb::id).toList();

        assertThat(ids).startsWith("sweep", "ship", "do");
        assertThat(ids.indexOf("ship")).isLessThan(ids.indexOf("focus"));
        assertThat(ids.indexOf("deploy")).isLessThan(ids.indexOf("done"));
    }

    @Test
    void servesTheSameCommandReferenceTheConsolePrints() {
        assertThat(api.help()).isEqualTo(dev.jagt.orchestrator.service.CommandReference.text());
    }

    @Test
    void servesTheSameSpendTextTheConsolePrints() {
        when(views.stats()).thenReturn("assistant token spend …");

        assertThat(api.stats()).isEqualTo("assistant token spend …");
    }

    @Test
    void reportsTheSessionSpendAndTheProjectsAlongsideTheTasks() {
        when(usageTracker.session()).thenReturn(dev.jagt.orchestrator.model.TokenUsage.ofCall(1000, 0, 50, 0.1));
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
        when(usageTracker.session()).thenReturn(dev.jagt.orchestrator.model.TokenUsage.NONE);
        when(taskViews.snapshot()).thenReturn(new TaskViews.Snapshot(List.of(),
                new dev.jagt.orchestrator.service.AutoReviewCadence(true, java.time.Duration.ofHours(24), 10, 60),
                List.of()));

        var board = api.tasks();

        assertThat(board.autoReviewEnabled()).isTrue();
        assertThat(board.autoReview()).isEqualTo("auto-review on · every 10–60m over 24h");
    }
}
