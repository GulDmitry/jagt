package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.service.StateService;
import lombok.RequiredArgsConstructor;
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

    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> mcp(@RequestBody String body,
                                        @RequestHeader(value = "X-Working-Directory", required = false) String cwd) {
        JsonNode message;
        try {
            message = mapper.readTree(body);
        } catch (RuntimeException e) {
            // Malformed JSON must answer a JSON-RPC -32700, never a Spring 500 page.
            return ResponseEntity.ok(protocolService.parseError(e.getMessage()));
        }
        return protocolService.handle(message, cwd)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public StateService.StateFile state() {
        return stateService.read();
    }
}
