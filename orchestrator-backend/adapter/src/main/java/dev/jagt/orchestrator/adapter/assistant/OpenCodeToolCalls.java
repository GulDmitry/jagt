package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.assistant.OpenCodeEvents.Call;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.ReadGate;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import tools.jackson.databind.JsonNode;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The run's tool calls held to its scope after the fact. A call the permissions denied never ran and passes;
 * one that ran outside the scope names itself.
 */
final class OpenCodeToolCalls {

    /** A glob's trailing {@code *} matches the rest of the line, so a redirect or a second command would pass it. */
    private static final Pattern CHAINED = Pattern.compile(">|;|&&|\\|\\||\\$\\(|`|\\n");
    private static final Map<String, String> TOOLS = Map.of("read", "Read", "grep", "Grep", "glob", "Glob",
            "shell", "Bash", "webfetch", "WebFetch");
    private static final Set<String> FILE_TOOLS = Set.of("Read", "Grep", "Glob");
    private static final Map<String, String> KEYS = Map.of("filePath", "file_path", "command", "command", "url", "url",
            "path", "path", "pattern", "pattern");

    private OpenCodeToolCalls() {
    }

    static Optional<String> violation(OpenCodeScope scope, List<Call> calls) {
        return calls.stream().filter(call -> "completed".equals(call.status()))
                .map(call -> refusal(scope, call)).flatMap(Optional::stream).findFirst();
    }

    private static Optional<String> refusal(OpenCodeScope scope, Call call) {
        String tool = TOOLS.get(call.tool());
        if (tool == null) {
            return Optional.of("tool " + call.tool());
        }
        JsonNode input = call.input();
        ReadScope read = new ReadScope(scope.roots(), List.of(), scope.git(), List.of(),
                scope.fetch() ? scope.environments() : List.of());
        boolean refused = "Bash".equals(tool) ? !shellAllowed(scope, input.path("command").asString(""))
                : FILE_TOOLS.contains(tool) && readRefused(scope, input);
        boolean gated = !refused && ReadGate.refusal(read, tool, arguments(input), null).isPresent();
        return refused || gated ? Optional.of(call.tool() + " " + OneLine.clipped(target(input))) : Optional.empty();
    }

    private static String target(JsonNode input) {
        return List.of("command", "url", "filePath", "path").stream().map(key -> input.path(key).asString("")).filter(value -> !value.isEmpty())
                .findFirst().orElse("");
    }

    private static Map<String, Object> arguments(JsonNode input) {
        Map<String, Object> arguments = new HashMap<>();
        KEYS.forEach((key, name) -> {
            if (input.path(key).isString()) {
                arguments.put(name, input.path(key).asString());
            }
        });
        return arguments;
    }

    private static boolean readRefused(OpenCodeScope scope, JsonNode input) {
        String path = input.path("filePath").asString(input.path("path").asString("."));
        return scope.readRefused().stream()
                .anyMatch(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(Path.of(path)));
    }

    private static boolean shellAllowed(OpenCodeScope scope, String command) {
        return scope.bashAllowed().stream().anyMatch(glob -> globMatches(glob, command))
                && scope.bashRefused().stream().noneMatch(glob -> globMatches(glob, command))
                && !CHAINED.matcher(command).find();
    }

    private static boolean globMatches(String glob, String command) {
        return Pattern.compile(Arrays.stream(glob.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*")),
                Pattern.DOTALL).matcher(command).matches();
    }
}
