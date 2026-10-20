package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdeLauncherTest {

    private final StateService stateService = mock(StateService.class);
    private final IdeProject project = mock(IdeProject.class);
    private final IdeDiff diff = mock(IdeDiff.class);

    @Test
    void opensATaskAsProjectsThatRunWhenAskedForTheProject() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(task));
        when(project.open("ABC-1", task)).thenReturn("Opened ABC-1");

        String opened = new IdeLauncher(stateService, project, diff).open("a1", IdeLauncher.Mode.PROJECT);

        assertThat(opened).isEqualTo("Opened ABC-1");
        verify(diff, never()).open("ABC-1", task);
    }

    @Test
    void opensATaskAsAStaticDiffWhenAskedForTheDiff() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(task));
        when(diff.open("ABC-1", task)).thenReturn("Opened STATIC diff of ABC-1");

        String opened = new IdeLauncher(stateService, project, diff).open("a1", IdeLauncher.Mode.DIFF);

        assertThat(opened).isEqualTo("Opened STATIC diff of ABC-1");
        verify(project, never()).open("ABC-1", task);
    }

    @Test
    void refusesATaskItDoesNotKnow() {
        when(stateService.canonicalTaskId("nope")).thenReturn("nope");
        when(stateService.task("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new IdeLauncher(stateService, project, diff).open("nope", IdeLauncher.Mode.DIFF))
                .isInstanceOf(Refusal.class);
    }
}
