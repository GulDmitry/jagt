package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A sub-agent is identified by the X-Working-Directory header its MCP config sets, and by nothing else. */
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
            throw new ToolRefusal(ToolFailure.PERMISSION, "Sub-agents may only act on their own task ("
                    + callerTaskId + "); omit taskId or use your own");
        }
        return canonical;
    }
}
