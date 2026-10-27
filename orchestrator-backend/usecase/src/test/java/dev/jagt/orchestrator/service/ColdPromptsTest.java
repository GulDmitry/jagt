package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ColdPromptsTest {

    private final WorktreeChanges changes = mock(WorktreeChanges.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void carriesThePromptWhatTheBoardKnowsGitsHistoryAndTheConversationsEndToAFreshSession() {
        TaskState task = TaskState.builder("proj", "/wt/ABC-42", TaskStatus.IN_PROGRESS).title("Add export")
                .message("two tests left").build();
        when(changes.history(task, 40)).thenReturn("# proj\ncommits:\nabc123 Add export\n");
        when(runtime.conversationTail(Path.of("/wt/ABC-42"), 16_000)).thenReturn(Optional.of("human: go on"));
        when(runtime.sessionLogOf(Path.of("/wt/ABC-42"))).thenReturn(Optional.of(Path.of("/logs/old.jsonl")));

        new ColdPrompts(changes, runtime, sessions).relay("ABC-42", task, "now handle empty files");

        verify(sessions).relayUnderCold("ABC-42", """
                now handle empty files

                ## Carried from the previous session

                ### Where it stood
                \u2014 Add export. On the board: IN_PROGRESS; your last report: two tests left

                ### Git
                # proj
                commits:
                abc123 Add export

                ### Its last words, newest last
                human: go on

                ### Whole transcript, to grep as a last resort
                /logs/old.jsonl
                """);
    }
}
