package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandBackDueTest {

    @Test
    void owesNotesWhenNoneWereWritten(@TempDir Path root) {
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
        HandBackDue due = new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class), mock(AgentRuntime.class));

        assertThat(due.owed("ABC-1", task)).contains("write task_notes.md before handing the round back");
    }

    @Test
    void owesAChangeToTheSpecsThatDoesNotHold(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "notes");
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
        WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
        when(worktreeChanges.agentFileLinesAdded(any())).thenReturn(Optional.of(0));
        Specs specs = mock(Specs.class);
        when(specs.owed(root, "ABC-1")).thenReturn(Optional.of("openspec/changes/abc-1 does not validate"));
        HandBackDue due = new HandBackDue(worktreeChanges, specs, mock(AgentRuntime.class));

        assertThat(due.owed("ABC-1", task)).contains("[proj] openspec/changes/abc-1 does not validate");
    }

    @Test
    void owesACountOfTheAgentFileLinesWhenGitCannotGiveIt(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "notes");
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
        HandBackDue due = new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class), mock(AgentRuntime.class));

        assertThat(due.owed("ABC-1", task))
                .contains("jagt could not count the lines this task added to the project's agent file");
    }

    @Test
    void findsInvalidANextModelTheRuntimeDoesNotOffer(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "next-model: gpt\n");
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.choosesModel()).thenReturn(true);
        when(runtime.models()).thenReturn(List.of("opus", "sonnet"));
        when(runtime.displayName()).thenReturn("Claude");
        HandBackDue due = new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class), runtime);

        assertThat(due.invalid(task)).contains(
                "task_notes.md names the model 'gpt', which Claude does not offer: one of opus, sonnet");
    }

    @Test
    void findsNothingInvalidInNotesNamingAnOfferedModel(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "next-model: sonnet\n");
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.choosesModel()).thenReturn(true);
        when(runtime.models()).thenReturn(List.of("opus", "sonnet"));
        HandBackDue due = new HandBackDue(mock(WorktreeChanges.class), mock(Specs.class), runtime);

        assertThat(due.invalid(task)).isEmpty();
    }

    @Test
    void owesANoteForEachAgentFileLineTheTaskAddedThatTheNotesDoNotName(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "notes");
        TaskState task = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
        WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
        when(worktreeChanges.agentFileLinesAdded(any())).thenReturn(Optional.of(1));
        HandBackDue due = new HandBackDue(worktreeChanges, mock(Specs.class), mock(AgentRuntime.class));

        assertThat(due.owed("ABC-1", task)).contains("task_notes.md names the gap for 0 of the 1 lines this task added"
                + " to the project's agent file: one `agent-file:` line each");
    }
}
