package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** A headless read takes its orders from the text it reads, so what it may call is bounded here, not in the prompt. */
@Component
@RequiredArgsConstructor
@Slf4j
class ReadOnlyTools {

    /** An allow glob needs the literal server before it, so a read is allowed by its verb, server by server. */
    private static final List<String> READ_VERBS = List.of("get", "list", "search", "read", "fetch", "query", "find",
            "describe", "lookup");

    /** Behind the fence, a second line: a write the human allowed everywhere is denied by its verb. */
    static final List<String> MCP_WRITES = Stream.concat(Stream.of("accept", "add", "approve", "archive", "assign",
                            "cancel", "click", "close", "comment", "create", "delete", "drag", "edit", "emulate",
                            "evaluate", "exec", "fill", "fork", "handle", "hover", "insert", "invite", "lighthouse",
                            "link", "manage", "mark", "merge", "move", "navigate", "new", "patch", "performance",
                            "post", "press", "publish", "push", "put", "python", "react", "remove", "rename",
                            "replace", "reply", "resolve", "respond", "run", "save", "schedule", "select", "send",
                            "set", "share", "start", "stop", "submit", "take", "transition", "trigger", "type",
                            "update", "upload", "wait", "write")
                    .map(verb -> "mcp__*__" + verb + "*"),
            // A read's own name may hold `merge` or `type`, so only these verbs are refused mid-name.
            Stream.of("audit", "clear", "create", "delete", "remove", "rename", "replace", "send", "start", "submit",
                            "update", "upload", "write")
                    .flatMap(verb -> Stream.of("mcp__*__*_" + verb, "mcp__*__*_" + verb + "_*")))
            .toList();

    private final McpHealth mcp;
    private final AssistantProperties assistant;
    private final ReadScopes scopes;
    private final HookEndpoint hooks;
    private final JsonMapper mapper = new JsonMapper();

    /** Every call of the run under {@code fence} is put to jagt first: a hook's deny beats any allow rule. */
    List<String> fence(String fence, ReadScope scope) {
        scopes.open(fence, scope);
        return List.of("--settings", mapper.writeValueAsString(Map.of("disableAllHooks", false,
                "hooks", Map.of("PreToolUse", List.of(Map.of("hooks", List.of(Map.of("type", "command",
                        "command", hooks.readGateCommand(fence), "timeout", 10))))))));
    }

    void lift(String fence) {
        scopes.close(fence);
    }

    /** The read-shaped tools of every server this kind of call loads, and the human's own list beside them. */
    List<String> allowed(AssistantCallKind kind) {
        String pinned = assistant.mcpConfigFor(kind);
        List<String> servers = pinned.isBlank() ? mcp.servers().orElse(List.of()) : declared(pinned);
        return Stream.concat(servers.stream()
                        .map(name -> "mcp__" + name.replaceAll("[^A-Za-z0-9_-]", "_") + "__")
                        .flatMap(prefix -> READ_VERBS.stream().map(verb -> prefix + verb + "*")),
                assistant.allowedTools().stream()).toList();
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
