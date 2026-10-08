package dev.jagt.orchestrator.adapter.assistant;

import java.util.List;
import java.util.stream.Stream;

/** A headless read takes its orders from the text it reads, so what it may call is bounded here, not in the prompt. */
final class ReadOnlyTools {

    /** MCP tools can be allowed by server or exact name only, never by pattern; a write is denied by its verb. */
    static final List<String> MCP_WRITES = Stream.of("accept", "add", "approve", "assign", "close", "create",
                    "delete", "edit", "execute", "fork", "link", "manage", "merge", "move", "post", "publish", "push",
                    "remove", "run", "save", "send", "set", "transition", "trigger", "update", "upload", "write")
            .map(verb -> "mcp__*__" + verb + "*")
            .toList();

    private ReadOnlyTools() {
    }
}
