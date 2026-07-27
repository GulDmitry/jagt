package dev.jawo.orchestrator.mcp;

import dev.jawo.orchestrator.model.TaskState;
import dev.jawo.orchestrator.service.StateService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
public class McpController {

    private final McpProtocolService protocolService;
    private final StateService stateService;
    private final ObjectMapper mapper;

    public McpController(McpProtocolService protocolService, StateService stateService, ObjectMapper mapper) {
        this.protocolService = protocolService;
        this.stateService = stateService;
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

    /** Plain-text dashboard rendered in the tmux "status" window. */
    @GetMapping(value = "/status", produces = MediaType.TEXT_PLAIN_VALUE)
    public String status() {
        Map<String, TaskState> tasks = stateService.tasks();
        StringBuilder out = new StringBuilder();
        out.append("jawo orchestrator — ").append(tasks.size()).append(" task(s)   updated ")
                .append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append('\n')
                .append('\n');
        out.append(String.format("%-6s %-12s %-16s %-10s %-12s %s%n",
                "ALIAS", "TASK", "STATUS", "PROJECT", "ACTIVE", "WORKTREE"));
        long now = System.currentTimeMillis();
        tasks.forEach((id, t) -> {
            long minutes = (now - t.lastActiveTimestamp()) / 60_000;
            String active = minutes < 1 ? "just now" : minutes + "m ago";
            out.append(String.format("%-6s %-12s %-16s %-10s %-12s %s%n",
                    t.alias() == null ? "-" : t.alias(), id, t.status(), t.project(), active, t.worktreePath()));
            String detail = dev.jawo.orchestrator.model.DashboardLine.forTask(id, t);
            if (!detail.isBlank()) {
                out.append("                    └ ").append(detail).append('\n');
            }
            out.append("                    → ").append(dev.jawo.orchestrator.model.NextMove.forStatus(t.status()))
                    .append('\n');
        });
        if (tasks.isEmpty()) {
            out.append("(no tasks)\n");
        }
        return out.toString();
    }
}
