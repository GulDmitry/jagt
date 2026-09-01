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

/** A gate that failed closed would make a stopped backend look like a rule, so anything not refused answers nothing. */
@RestController
@RequiredArgsConstructor
public class AgentToolGateController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(@JsonProperty("tool_name") String toolName,
                           @JsonProperty("tool_input") Map<String, Object> toolInput) {

        String command() {
            Object command = toolInput == null ? null : toolInput.get("command");
            return command == null ? null : command.toString();
        }
    }

    private final StateService stateService;

    /** Whatever comes back here is printed into the session, so an allowed call answers no body. */
    @PostMapping(value = "/api/agent/tool", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> gate(
            @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
            @RequestBody(required = false) ToolCall call) {
        if (call == null) {
            return ResponseEntity.noContent().build();
        }
        // A directory no task owns has no branch a push could be refused against.
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
