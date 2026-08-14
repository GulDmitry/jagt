package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which task a call may act on. The caller is identified by the worktree it runs in (the
 * {@code X-Working-Directory} header), and a sub-agent gets exactly one: its own.
 */
@Component
@RequiredArgsConstructor
public class CallerScope {

    private final StateService stateService;

    /** A sub-agent that names no task means itself; one that names a sibling is refused, not corrected. */
    public String resolve(String explicitTaskId, String callerTaskId) {
        if (explicitTaskId == null || explicitTaskId.isBlank()) {
            if (callerTaskId == null) {
                throw new IllegalArgumentException(
                        "taskId is required: caller is not inside a registered worktree");
            }
            return callerTaskId;
        }
        String canonical = stateService.canonicalTaskId(explicitTaskId);
        if (callerTaskId != null && !canonical.equals(callerTaskId)) {
            throw new IllegalArgumentException("Sub-agents may only act on their own task ("
                    + callerTaskId + "); omit taskId or use your own");
        }
        return canonical;
    }

    /** What writes outside the worktree is the human's alone. */
    public void requireMaster(String callerTaskId, String tool) {
        if (callerTaskId != null) {
            throw new IllegalArgumentException(tool + " is Master-only: a sub-agent may only act inside its"
                    + " own worktree");
        }
    }
}
