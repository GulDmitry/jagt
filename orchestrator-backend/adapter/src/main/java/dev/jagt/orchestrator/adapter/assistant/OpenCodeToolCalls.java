package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.assistant.OpenCodeEvents.Call;
import dev.jagt.orchestrator.service.OneLine;
import tools.jackson.databind.JsonNode;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A file the role refuses: the gate holds no such list, so a completed call on one is read back from the run. */
final class OpenCodeToolCalls {

    private static final Set<String> FILE_TOOLS = Set.of("read", "grep", "glob");

    private OpenCodeToolCalls() {
    }

    static Optional<String> violation(OpenCodeScope scope, List<Call> calls) {
        return calls.stream().filter(call -> "completed".equals(call.status()) && FILE_TOOLS.contains(call.tool()))
                .filter(call -> refused(scope, call.input()))
                .map(call -> call.tool() + " " + OneLine.clipped(path(call.input()))).findFirst();
    }

    private static String path(JsonNode input) {
        return input.path("filePath").asString(input.path("path").asString("."));
    }

    private static boolean refused(OpenCodeScope scope, JsonNode input) {
        Path path = Path.of(path(input));
        return scope.readRefused().stream()
                .anyMatch(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(path));
    }
}
