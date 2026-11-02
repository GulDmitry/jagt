package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * What a fresh session is given of the one it replaces, gathered without a model: the board's facts, git's, the
 * conversation's own last words. A session that kept no transcript carries nothing.
 */
@Component
@RequiredArgsConstructor
public class CarriedBlock {

    static final int MAX_GIT_LINES = 40;
    static final int MAX_CONVERSATION_CHARS = 16_000;
    private static final String HEADING = "\n\n## Carried from the previous session\n\n";

    private final WorktreeChanges changes;
    private final AgentRuntime runtime;

    /** The instructions first, the block after them; a block they already end with is replaced, never doubled. */
    String after(String instructions, TaskState task) {
        String own = without(instructions);
        Path worktree = Path.of(task.worktreePath());
        if (runtime.sessionLogOf(worktree).isEmpty()) {
            return own;
        }
        StringBuilder carried = new StringBuilder(own).append(HEADING)
                .append("### Where it stood\n").append(TaskFacts.of(task).stripLeading()).append("\n\n")
                .append("### Git\n").append(changes.history(task, MAX_GIT_LINES)).append('\n');
        runtime.conversationTail(worktree, MAX_CONVERSATION_CHARS).ifPresent(tail ->
                carried.append("### Its last words, newest last\n").append(tail).append("\n\n"));
        runtime.sessionLogOf(worktree).ifPresent(log ->
                carried.append("### Whole transcript, to grep as a last resort\n").append(log).append('\n'));
        return carried.toString();
    }

    static String without(String text) {
        int at = text.indexOf(HEADING);
        return at < 0 ? text : text.substring(0, at);
    }
}
