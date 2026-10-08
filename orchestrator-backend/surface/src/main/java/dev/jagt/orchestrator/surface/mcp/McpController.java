package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequiredArgsConstructor
public class McpController {

    private final McpProtocolService protocolService;
    private final StateService stateService;
    private final ObjectMapper mapper;
    private final MasterToken masterToken;

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> mcp(@RequestBody String body,
                                        @RequestHeader(value = "X-Working-Directory", required = false) String cwd,
                                        @RequestHeader(value = MasterToken.HEADER, required = false) String token) {
        JsonNode message;
        try {
            message = mapper.readTree(body);
        } catch (RuntimeException e) {
            // Malformed JSON must answer a JSON-RPC -32700, never a Spring 500 page.
            return ResponseEntity.ok(protocolService.parseError(e.getMessage()));
        }
        return protocolService.handle(message, cwd, masterToken.matches(token))
                .map(answer -> answer.path("error").path("code").asInt() == McpProtocolService.UNKNOWN_CALLER
                        ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(answer) : ResponseEntity.ok(answer))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public StateService.StateFile state() {
        return stateService.read();
    }
}
