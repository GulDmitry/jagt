package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskArtifactsTest {

    @TempDir
    Path worktree;

    private final StateService state = mock(StateService.class);
    private final TaskArtifacts artifacts = new TaskArtifacts(state);

    private void taskWithWorktree() {
        when(state.canonicalTaskId("ABC-42")).thenReturn("ABC-42");
        when(state.task("ABC-42")).thenReturn(Optional.of(
                TaskState.builder("web", worktree.toString(), TaskStatus.IN_PROGRESS).build()));
    }

    @Test
    void listsOnlyTheDocumentsThatExistInTheWorktree() throws Exception {
        taskWithWorktree();
        Files.writeString(worktree.resolve("plan.md"), "# plan");
        Files.writeString(worktree.resolve("task_visual.md"), "sketch");
        Files.writeString(worktree.resolve("README.md"), "not a document of a task");

        assertThat(artifacts.present("ABC-42")).containsExactly("plan.md", "task_visual.md");
    }

    @Test
    void listsThePlanAndTheSketchFirstAndTheReviewersNotesLast() throws Exception {
        taskWithWorktree();
        Files.writeString(worktree.resolve("master-review.md"), "verdict");
        Files.writeString(worktree.resolve("master-decisions.md"), "decided");
        Files.writeString(worktree.resolve("task_request.md"), "asked");
        Files.writeString(worktree.resolve("task_visual.md"), "sketch");
        Files.writeString(worktree.resolve("plan.md"), "# plan");

        assertThat(artifacts.present("ABC-42")).containsExactly(
                "plan.md", "task_visual.md", "task_request.md", "master-decisions.md", "master-review.md");
    }

    @Test
    void listsNothingForAWorktreeHoldingNoDocument() {
        taskWithWorktree();

        assertThat(artifacts.present("ABC-42")).isEmpty();
    }

    @Test
    void readsTheDocumentAsItStandsOnDiskNow() throws Exception {
        taskWithWorktree();
        Files.writeString(worktree.resolve("plan.md"), "first");
        artifacts.read("ABC-42", "plan.md");
        Files.writeString(worktree.resolve("plan.md"), "second");

        assertThat(artifacts.read("ABC-42", "plan.md")).isEqualTo("second");
    }

    @Test
    void refusesANameOutsideTheDocumentsOfATaskWithoutTouchingTheDisk() throws Exception {
        taskWithWorktree();
        Files.writeString(worktree.resolve("secret.txt"), "x");

        assertThatThrownBy(() -> artifacts.read("ABC-42", "../secret.txt"))
                .isInstanceOf(TaskArtifacts.UnknownArtifact.class);
        assertThatThrownBy(() -> artifacts.read("ABC-42", "secret.txt"))
                .isInstanceOf(TaskArtifacts.UnknownArtifact.class);
    }

    @Test
    void saysAKnownDocumentIsAbsentWhenTheWorktreeHoldsNoSuchFile() {
        taskWithWorktree();

        assertThatThrownBy(() -> artifacts.read("ABC-42", "plan.md"))
                .isInstanceOf(TaskArtifacts.ArtifactAbsent.class);
    }

    @Test
    void saysADocumentIsUnreadableRatherThanAbsentWhenItCannotBeRead() throws Exception {
        taskWithWorktree();
        Files.write(worktree.resolve("plan.md"), new byte[(int) TaskArtifacts.MAX_BYTES + 1]);

        assertThatThrownBy(() -> artifacts.read("ABC-42", "plan.md"))
                .isInstanceOf(TaskArtifacts.ArtifactUnreadable.class);
    }

    @Test
    void refusesATaskThatIsGone() {
        when(state.canonicalTaskId("ABC-9")).thenReturn("ABC-9");
        when(state.task("ABC-9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artifacts.present("ABC-9")).isInstanceOf(Refusal.class);
        assertThatThrownBy(() -> artifacts.read("ABC-9", "plan.md")).isInstanceOf(Refusal.class);
    }

    @Test
    void readsADocumentOfExactlyTheSizeCeiling() throws Exception {
        taskWithWorktree();
        Files.write(worktree.resolve("plan.md"), new byte[(int) TaskArtifacts.MAX_BYTES]);

        assertThat(artifacts.read("ABC-42", "plan.md")).hasSize((int) TaskArtifacts.MAX_BYTES);
    }

    @Test
    void refusesATaskWhoseWorktreePathIsBlankRatherThanReadingTheWorkingDirectory() {
        when(state.canonicalTaskId("ABC-42")).thenReturn("ABC-42");
        when(state.task("ABC-42")).thenReturn(Optional.of(
                TaskState.builder("web", "", TaskStatus.IN_PROGRESS).build()));

        assertThatThrownBy(() -> artifacts.present("ABC-42")).isInstanceOf(Refusal.class);
    }
}
