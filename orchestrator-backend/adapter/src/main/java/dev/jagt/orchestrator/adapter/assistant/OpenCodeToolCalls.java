package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.assistant.OpenCodeEvents.Call;
import dev.jagt.orchestrator.service.OneLine;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The run's tool calls held to its scope after the fact. A call the permissions denied never ran and passes;
 * one that ran outside the scope names itself.
 */
final class OpenCodeToolCalls {

    private OpenCodeToolCalls() {
    }

    static Optional<String> violation(OpenCodeScope scope, List<Call> calls) {
        return calls.stream().filter(call -> "completed".equals(call.status()))
                .map(call -> refusal(scope, call)).flatMap(Optional::stream).findFirst();
    }

    private static Optional<String> refusal(OpenCodeScope scope, Call call) {
        JsonNode input = call.input();
        return switch (call.tool()) {
            case "read", "grep", "glob" -> file(scope, call.tool(), input);
            case "shell" -> shell(scope, input.path("command").asString(""));
            case "webfetch" -> fetch(scope, input.path("url").asString(""));
            default -> Optional.of("tool " + call.tool());
        };
    }

    private static Optional<String> file(OpenCodeScope scope, String tool, JsonNode input) {
        String path = input.path("filePath").asString(input.path("path").asString("."));
        boolean globEscapes = "glob".equals(tool) && input.path("pattern").asString("").contains("..");
        boolean refused = scope.readRefused().stream().anyMatch(glob -> matches(glob, path));
        return globEscapes || refused || !inside(scope, path) ? Optional.of(tool + " " + path) : Optional.empty();
    }

    private static boolean matches(String glob, String path) {
        return FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(Path.of(path));
    }

    private static Optional<String> shell(OpenCodeScope scope, String command) {
        boolean allowed = scope.bashAllowed().stream().anyMatch(glob -> globMatches(glob, command))
                && scope.bashRefused().stream().noneMatch(glob -> globMatches(glob, command))
                && !command.contains(">");
        return allowed ? Optional.empty() : Optional.of("shell " + OneLine.clipped(command));
    }

    private static Optional<String> fetch(OpenCodeScope scope, String url) {
        boolean listed = scope.fetch() && scope.environments().stream().anyMatch(environment -> url.equals(environment)
                || url.startsWith(environment) && (environment.endsWith("/") || "/?#".indexOf(url.charAt(environment.length())) >= 0));
        return listed ? Optional.empty() : Optional.of("webfetch " + url);
    }

    private static boolean globMatches(String glob, String command) {
        return Pattern.compile(Arrays.stream(glob.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*")),
                Pattern.DOTALL).matcher(command).matches();
    }

    private static boolean inside(OpenCodeScope scope, String path) {
        if (path.startsWith("~")) {
            return false;
        }
        Path target = real(scope.roots().getFirst().resolve(path));
        return scope.roots().stream().map(OpenCodeToolCalls::real).anyMatch(target::startsWith);
    }

    private static Path real(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException e) {
            Path parent = absolute.getParent();
            return parent == null ? absolute : real(parent).resolve(absolute.getFileName());
        }
    }
}
