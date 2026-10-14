package dev.jagt.orchestrator.surface.agent;

import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.service.ToolGate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/** A gate that failed closed would make a stopped backend look like a rule, so anything not refused answers nothing. */
@RestController
@Slf4j
public class AgentToolGateController {

    /** Only a shell command can push; every other tool is answered with nothing. */
    private static final String SHELL_TOOL = "Bash";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(@JsonProperty("tool_name") @Nullable String toolName,
                           @JsonProperty("tool_input") @Nullable Map<String, Object> toolInput) {

        @Nullable String command() {
            Object command = toolInput == null ? null : toolInput.get("command");
            return command == null ? null : command.toString();
        }
    }

    private final StateService stateService;

    public AgentToolGateController(StateService stateService) {
        this.stateService = stateService;
    }

    /** Whatever comes back here is printed into the session, so an allowed call answers no body. */
    @PostMapping(value = "/api/agent/tool", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> gate(
            @RequestHeader(value = "X-Working-Directory", required = false) @Nullable String cwd,
            @RequestBody(required = false) @Nullable ToolCall call, HttpServletRequest request) {
        if (call == null || !SHELL_TOOL.equalsIgnoreCase(call.toolName())) {
            return ResponseEntity.noContent().build();
        }
        // A directory no task owns has no branch a push could be refused against.
        String taskBranch = stateService.findByWorktree(cwd).map(Map.Entry::getKey).orElse(null);
        return ToolGate.refusal(call.command(), taskBranch, request.getLocalPort())
                .map(reason -> {
                    stateService.updateTask(Objects.requireNonNull(taskBranch),
                            task -> task.refused(Guard.SESSION_LINE));
                    log.atInfo().setMessage("session line refused").addKeyValue("task", taskBranch).log();
                    return ResponseEntity.ok(denied(reason));
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** The CLI's own shape for a refusal a model is meant to read. */
    static Map<String, Object> denied(String reason) {
        return Map.of("continue", true,
                "hookSpecificOutput", Map.of(
                        "hookEventName", "PreToolUse",
                        "permissionDecision", "deny",
                        "permissionDecisionReason", reason));
    }
}
