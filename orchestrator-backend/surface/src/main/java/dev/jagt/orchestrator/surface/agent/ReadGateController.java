package dev.jagt.orchestrator.surface.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jagt.orchestrator.service.ReadScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/** Fails closed, unlike a session's gate: a read runs only while jagt does, so no answer means nothing runs. */
@RestController
@RequiredArgsConstructor
public class ReadGateController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(@JsonProperty("tool_name") String toolName,
                           @JsonProperty("tool_input") Map<String, Object> toolInput,
                           @JsonProperty("cwd") String cwd) {
    }

    private final ReadScopes scopes;

    @PostMapping(value = "/api/agent/read/{fence}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> gate(@PathVariable String fence,
                                                    @RequestBody(required = false) ToolCall call) {
        Optional<String> refusal = call == null
                ? Optional.of("jagt refuses a call it cannot read.")
                : scopes.ask(fence)
                        .map(scope -> ReadGate.refusal(scope, call.toolName(), call.toolInput(), call.cwd()))
                        .orElse(Optional.of("jagt refuses a call it holds no read for."));
        return refusal.map(reason -> ResponseEntity.ok(AgentToolGateController.denied(reason)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
