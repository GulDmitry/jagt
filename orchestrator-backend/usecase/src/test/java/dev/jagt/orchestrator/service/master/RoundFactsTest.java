package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
}
