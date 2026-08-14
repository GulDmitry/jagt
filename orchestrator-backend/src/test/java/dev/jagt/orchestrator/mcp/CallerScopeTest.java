package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The scoping rule an agent cannot argue with: it acts on its own task or on nothing. The classic failure is
 * an id mix-up, which without this lands a status update — or a teardown — on a sibling's task.
 */
class CallerScopeTest {

    private final StateService stateService = mock(StateService.class);
    private final CallerScope scope = new CallerScope(stateService);

    @Test
    void readsAnOmittedTaskAsTheCallersOwn() {
        assertThat(scope.resolve(null, "ABC-1")).isEqualTo("ABC-1");
        assertThat(scope.resolve("  ", "ABC-1")).isEqualTo("ABC-1");
    }

    @Test
    void resolvesAnAliasToTheTaskItNames() {
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");

        assertThat(scope.resolve("a1", null)).isEqualTo("ABC-1");
    }

    @Test
    void refusesASubAgentThatNamesASiblingRatherThanCorrectingIt() {
        when(stateService.canonicalTaskId("OTHER-1")).thenReturn("OTHER-1");

        assertThatThrownBy(() -> scope.resolve("OTHER-1", "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only act on their own task");
    }

    @Test
    void refusesACallFromOutsideAnyWorktreeThatNamesNoTask() {
        assertThatThrownBy(() -> scope.resolve(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caller is not inside a registered worktree");
    }

    @ParameterizedTest
    @ValueSource(strings = {"remove_task", "deploy_task", "revert_task"})
    void keepsWhatWritesOutsideTheWorktreeWithTheHuman(String tool) {
        assertThatThrownBy(() -> scope.requireMaster("MINE-1", tool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(tool + " is Master-only");
    }

    @Test
    void letsTheMasterThroughBecauseItRunsInNoWorktree() {
        scope.requireMaster(null, "deploy_task");
    }
}
