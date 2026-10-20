package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundFactsTest {

    private final TicketTexts tickets = mock(TicketTexts.class);

    @Test
    void quotesWhatATaskNobodyFiledWasOpenedWith(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve("task_request.md"), "make the page size configurable");
        when(tickets.of("ABC-1")).thenReturn(Optional.empty());
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();

        String ask = new RoundFacts(tickets, mock(WorktreeChanges.class), mock(AgentRuntime.class)).ask("ABC-1", task);

        assertThat(ask).isEqualTo("make the page size configurable");
    }

    @Test
    void quotesTheTicketWhereOneWasRead(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve("task_request.md"), "Implement ABC-1");
        when(tickets.of("ABC-1")).thenReturn(Optional.of("Summary: accept v3 calls"));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();

        String ask = new RoundFacts(tickets, mock(WorktreeChanges.class), mock(AgentRuntime.class)).ask("ABC-1", task);

        assertThat(ask).isEqualTo("Summary: accept v3 calls");
    }

    @Test
    void quotesTheDiffOfTheRoundAsTheChangesReadIt(@TempDir Path worktree) {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(changes.diff(task)).thenReturn("+int x;\n");

        String diff = new RoundFacts(tickets, changes, mock(AgentRuntime.class)).diff(task);

        assertThat(diff).isEqualTo("+int x;\n");
    }

    @Test
    void listsTheChangedPathsOfEveryRepositoryTogether(@TempDir Path worktree) {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(changes.paths(task)).thenReturn(Optional.of(Map.of("api", List.of("A.java", "B.java"),
                "web", List.of("c.js"))));

        Optional<List<String>> paths = new RoundFacts(tickets, changes, mock(AgentRuntime.class)).paths(task);

        assertThat(paths).hasValueSatisfying(all -> assertThat(all).containsExactlyInAnyOrder("A.java", "B.java", "c.js"));
    }

    @Test
    void givesTheTasksOwnDiffAsTheChangesReadIt(@TempDir Path worktree) {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(changes.ownDiff(task)).thenReturn(Optional.of("abc123"));

        Optional<String> own = new RoundFacts(tickets, changes, mock(AgentRuntime.class)).ownDiff(task);

        assertThat(own).contains("abc123");
    }

    @Test
    void quotesTheNotesTheSessionLeftWithoutTheirPadding(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(Artifact.NOTES.fileName()), "\n disputed: the guard is right \n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();

        String notes = new RoundFacts(tickets, mock(WorktreeChanges.class), mock(AgentRuntime.class)).notes(task);

        assertThat(notes).isEqualTo("disputed: the guard is right");
    }

    @Test
    void quotesThePlanTheSessionWroteWithoutItsPadding(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve(Artifact.PLAN.fileName()), "\n1. add the v3 route\n");
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();

        String plan = new RoundFacts(tickets, mock(WorktreeChanges.class), mock(AgentRuntime.class)).plan(task);

        assertThat(plan).isEqualTo("1. add the v3 route");
    }

    @Test
    void quotesWhatTheHumanTypedAsTheAgentRecordHoldsIt(@TempDir Path worktree) {
        AgentRuntime runtime = mock(AgentRuntime.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(runtime.humanSaid(worktree, Set.of(AgentSessions.NUDGE))).thenReturn(Optional.of(List.of("keep v2")));

        Optional<List<String>> said = new RoundFacts(tickets, mock(WorktreeChanges.class), runtime).humanSaid(task);

        assertThat(said).contains(List.of("keep v2"));
    }
}
