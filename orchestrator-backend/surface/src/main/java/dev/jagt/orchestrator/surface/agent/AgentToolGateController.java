package dev.jagt.orchestrator.surface.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.ToolGate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * What a session asks before it runs a tool, and the one answer jagt has: a push whose destination is not the
 * task's own branch is refused, with the reason the model then reads.
 *
 * <p>Every other call is answered with NOTHING, which is what lets the CLI carry on as if jagt had never been
 * asked — and the same happens when jagt is unreachable. A gate that fails closed would make a stopped backend
 * look like a rule.
 */
@RestController
@RequiredArgsConstructor
public class AgentToolGateController {

    /** The call about to run, in the CLI's own shape: which tool, and the command it was given. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(@JsonProperty("tool_name") String toolName,
                           @JsonProperty("tool_input") Map<String, Object> toolInput) {

        String command() {
            Object command = toolInput == null ? null : toolInput.get("command");
            return command == null ? null : command.toString();
        }
    }

    private final StateService stateService;

    /** An allowed call answers NO BODY at all: whatever comes back here is printed into the session. */
    @PostMapping(value = "/api/agent/tool", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> gate(
            @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
            @RequestBody(required = false) ToolCall call) {
        if (call == null) {
            return ResponseEntity.noContent().build();
        }
        // A call from a directory no task owns is nobody's to refuse: the branch it may push is unknown.
        String taskBranch = stateService.findByWorktree(cwd).map(Map.Entry::getKey).orElse(null);
        return ToolGate.refusal(call.toolName(), call.command(), taskBranch)
                .map(reason -> ResponseEntity.ok(denied(reason)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** The CLI's own shape for a refusal a model is meant to read. */
    private static Map<String, Object> denied(String reason) {
        return Map.of("continue", true,
                "hookSpecificOutput", Map.of(
                        "hookEventName", "PreToolUse",
                        "permissionDecision", "deny",
                        "permissionDecisionReason", reason));
    }
}
