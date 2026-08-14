package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommandServiceTest {

    private final TaskOperations operations = mock(TaskOperations.class);
    private final DeployService deploys = mock(DeployService.class);
    private final ReviewSweepService reviewSweep = mock(ReviewSweepService.class);
    private final ShipService shipService = mock(ShipService.class);
    private final StateService stateService = mock(StateService.class);
    private final CommandService commands = new CommandService(operations, deploys, reviewSweep, shipService, stateService);

    @BeforeEach
    void tasksAreAddressedByTheirIdUnlessATestSaysOtherwise() {
        when(stateService.canonicalTaskId(anyString())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void refusesAnActionTheTasksStatusDoesNotAllowInsteadOfLettingGitFailLater() {
        // A board tab open since before the task was created would still show a Deploy button; the refusal has
        // to happen here, in words, not as a git error three layers down.
        havingTask("ABC-1", TaskStatus.IN_PROGRESS, null);

        assertThatThrownBy(() -> commands.execute("ABC-1", TaskAction.DEPLOY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Deploy is not available for ABC-1")
                .hasMessageContaining("IN_PROGRESS");
        verifyNoInteractions(reviewSweep);
    }

    @Test
    void marksTheTwoRefusalsThatMeanTheCallersViewIsOutOfDate() {
        havingTask("ABC-1", TaskStatus.IN_PROGRESS, null);

        assertThatThrownBy(() -> commands.execute("ABC-1", TaskAction.DEPLOY))
                .asInstanceOf(type(Refusal.class))
                .extracting(Refusal::code).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE);
        assertThatThrownBy(() -> commands.execute("GONE-1", TaskAction.FOCUS))
                .asInstanceOf(type(Refusal.class))
                .extracting(Refusal::code).isEqualTo(Refusal.Code.NO_SUCH_TASK);
    }

    @Test
    void runsALegalActionAndHandsBackWhateverTheToolSaid() {
        havingTask("ABC-1", TaskStatus.REVIEW_PENDING, null);
        when(shipService.ship("ABC-1")).thenReturn("ship ABC-1: approval relayed");

        assertThat(commands.execute("ABC-1", TaskAction.SHIP)).isEqualTo("ship ABC-1: approval relayed");
    }

    @Test
    void takesAnAliasEverywhereTheConsoleDoes() {
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        havingTask("ABC-1", TaskStatus.IN_PROGRESS, null);

        commands.execute("a1", TaskAction.FOCUS);

        verify(operations).focus("ABC-1");
    }

    @Test
    void saysTheTaskIsGoneRatherThanFailingObscurelyWhenAnotherTabClosedIt() {
        when(stateService.task("ABC-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commands.execute("ABC-1", TaskAction.FOCUS))
                .hasMessageContaining("No task ABC-1")
                .hasMessageContaining("closed since this page loaded");
    }

    @Test
    void sweepsTheReviewThroughTheSharedServiceSoTheGuardStillApplies() {
        havingTask("ABC-1", TaskStatus.CI_POLLING, "http://mr/1");
        when(reviewSweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.PENDING, "still waiting"));

        assertThat(commands.execute("ABC-1", TaskAction.SWEEP)).isEqualTo("still waiting");
    }

    private void havingTask(String taskId, TaskStatus status, String reviewRequestUrl) {
        when(stateService.task(taskId)).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", status).alias("a1").mrUrl(reviewRequestUrl).build()));
    }
}
