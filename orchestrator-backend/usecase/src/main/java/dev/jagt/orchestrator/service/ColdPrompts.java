package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * A prompt typed into a session gone cold, carried to a fresh one with what that session would otherwise lose,
 * gathered without a model: the board's facts, git's, the conversation's own last words.
 */
@Component
@RequiredArgsConstructor
public class ColdPrompts {

    static final int MAX_GIT_LINES = 40;
    static final int MAX_CONVERSATION_CHARS = 16_000;

    private final WorktreeChanges changes;
    private final AgentRuntime runtime;
    private final AgentSessions sessions;

    /** The prompt is on disk and the fresh window launched when this returns; any failure is thrown, nothing swallowed. */
    public void relay(String taskId, TaskState task, String prompt) {
        sessions.relayUnderCold(taskId, carried(prompt, task));
    }

    String carried(String prompt, TaskState task) {
        Path worktree = Path.of(task.worktreePath());
        StringBuilder carried = new StringBuilder(prompt).append("\n\n## Carried from the previous session\n\n")
                .append("### Where it stood\n").append(TaskFacts.of(task).stripLeading()).append("\n\n")
                .append("### Git\n").append(changes.history(task, MAX_GIT_LINES)).append('\n');
        runtime.conversationTail(worktree, MAX_CONVERSATION_CHARS).ifPresent(tail ->
                carried.append("### Its last words, newest last\n").append(tail).append("\n\n"));
        runtime.sessionLogOf(worktree).ifPresent(log ->
                carried.append("### Whole transcript, to grep as a last resort\n").append(log).append('\n'));
        return carried.toString();
    }
}
