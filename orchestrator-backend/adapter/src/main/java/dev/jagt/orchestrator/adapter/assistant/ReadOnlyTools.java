package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** A headless read takes its orders from the text it reads, so what it may call is bounded here, not in the prompt. */
@Component
@RequiredArgsConstructor
@Slf4j
class ReadOnlyTools {

    /** An allow glob needs the literal server before it, so a read is allowed by its verb, server by server. */
    private static final List<String> READ_VERBS = List.of("get", "list", "search", "read", "fetch", "query", "find",
            "describe", "lookup");

    /** The human's own allow rules still load; a write they allowed everywhere is denied here by its verb. */
    static final List<String> MCP_WRITES = Stream.of("accept", "add", "approve", "archive", "assign", "cancel",
                    "click", "close", "comment", "create", "delete", "drag", "edit", "emulate", "evaluate", "exec",
                    "fill", "fork", "handle", "insert", "invite", "link", "manage", "mark", "merge", "move", "navigate",
                    "patch", "post", "press", "publish", "push", "put", "python", "react", "remove", "rename",
                    "replace", "reply", "resolve", "respond", "run", "save", "schedule", "send", "set", "share",
                    "start", "stop", "submit", "transition", "trigger", "type", "update", "upload", "write")
            .map(verb -> "mcp__*__" + verb + "*")
            .toList();

    private final McpHealth mcp;
    private final AssistantProperties assistant;
    private final JsonMapper mapper = new JsonMapper();

    /** The human's own list, else the read-shaped tools of every server this kind of call loads. */
    List<String> allowed(AssistantCallKind kind) {
        if (!assistant.allowedTools().isEmpty()) {
            return assistant.allowedTools();
        }
        String pinned = assistant.mcpConfigFor(kind);
        List<String> servers = pinned.isBlank() ? mcp.servers().orElse(List.of()) : declared(pinned);
        return servers.stream()
                .map(name -> "mcp__" + name.replaceAll("[^A-Za-z0-9_-]", "_") + "__")
                .flatMap(prefix -> READ_VERBS.stream().map(verb -> prefix + verb + "*"))
                .toList();
    }

    private List<String> declared(String pinned) {
        try {
            String json = pinned.strip().startsWith("{") ? pinned : Files.readString(Path.of(pinned));
            return List.copyOf(mapper.readTree(json).path("mcpServers").propertyNames());
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("mcp config unreadable")
                    .addKeyValue("path", pinned)
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "no mcp tool allowed")
                    .log();
            return List.of();
        }
    }
}
