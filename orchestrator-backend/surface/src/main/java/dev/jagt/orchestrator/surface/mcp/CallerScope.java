package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** A caller is identified by the X-Working-Directory header its stdio bridge sets, and by nothing else. */
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

    public void requireMaster(String callerTaskId, String tool) {
        if (callerTaskId != null) {
            throw new IllegalArgumentException(tool + " is Master-only: a sub-agent may only act inside its"
                    + " own worktree");
        }
    }
}
