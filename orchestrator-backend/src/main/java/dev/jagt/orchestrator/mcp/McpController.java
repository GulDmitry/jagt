package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StateViews;
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
public class McpController {

    private final McpProtocolService protocolService;
    private final StateService stateService;
    private final StateViews views;
    private final ObjectMapper mapper;

    public McpController(McpProtocolService protocolService, StateService stateService,
                         StateViews views, ObjectMapper mapper) {
        this.protocolService = protocolService;
        this.stateService = stateService;
        this.views = views;
        this.mapper = mapper;
    }

    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> mcp(@RequestBody String body,
                                        @RequestHeader(value = "X-Working-Directory", required = false) String cwd) {
        JsonNode message;
        try {
            message = mapper.readTree(body);
        } catch (RuntimeException e) {
            // Malformed JSON must yield a JSON-RPC -32700, never a Spring 500 page:
            // the stdio proxy pipes our body verbatim to the MCP client.
            return ResponseEntity.ok(protocolService.parseError(e.getMessage()));
        }
        return protocolService.handle(message, cwd)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Human observability endpoint: current state.json content. */
    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public StateService.StateFile state() {
        return stateService.read();
    }

    /** Plain-text dashboard (same view the Master shell prints). */
    @GetMapping(value = "/status", produces = MediaType.TEXT_PLAIN_VALUE)
    public String status() {
        return views.dashboard();
    }

    /** Plain-text token spend of jagt's own model calls, per task (same view as the `stats` command). */
    @GetMapping(value = "/stats", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stats() {
        return views.usageStats();
    }

    /** Worktree directories no task owns any more, and how many copied secret files they still hold. */
    @GetMapping(value = "/orphans", produces = MediaType.TEXT_PLAIN_VALUE)
    public String orphans() {
        return views.orphanedWorktrees();
    }
}
