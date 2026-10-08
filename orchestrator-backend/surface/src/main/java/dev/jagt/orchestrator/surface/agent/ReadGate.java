package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a headless read may call, answered tool by tool: the human's own allow rules cannot widen it. A file tool
 * stays inside the scope's roots, the shell runs one read-only git command, and any other tool must be named.
 */
public final class ReadGate {

    private static final String GLOB = "Glob";
    private static final Set<String> FILE_TOOLS = Set.of("Read", "Grep", GLOB);
    private static final List<String> PATH_KEYS = List.of("file_path", "path");
    private static final String SHELL_TOOL = "Bash";
    /** A brace expansion's alternatives are paths too. */
    static final Pattern PARENT = Pattern.compile("(^|[/{,])\\.\\.([/},]|$)");

    private ReadGate() {
    }

    /** Why the call is refused, or empty when it may go on to the CLI's own permission rules. */
    public static Optional<String> refusal(ReadScope read, String tool, Map<String, Object> input, String cwd) {
        if (read.tools().stream().anyMatch(named -> names(named, tool))) {
            return Optional.empty();
        }
        Map<String, Object> arguments = input == null ? Map.of() : input;
        Path here = cwd == null || cwd.isBlank() ? read.roots().stream().findFirst().orElse(Path.of("/")) : Path.of(cwd);
        if (FILE_TOOLS.contains(tool)) {
            return fileRefusal(read, arguments, here, GLOB.equals(tool));
        }
        if (SHELL_TOOL.equals(tool) && read.git()) {
            return GitReadLine.refusal(read, String.valueOf(arguments.getOrDefault("command", "")), here);
        }
        return Optional.of("jagt refuses " + tool + " in a read: only the tools it was given may run.");
    }

    private static boolean names(String named, String tool) {
        if (named.contains("*")) {
            return Pattern.compile(Pattern.quote(named).replace("*", "\\E.*\\Q")).matcher(tool).matches();
        }
        return tool.equals(named) || tool.startsWith(named + "__");
    }

    /** A search naming no path searches where the call runs; a glob's own pattern may name a directory. */
    private static Optional<String> fileRefusal(ReadScope read, Map<String, Object> arguments, Path here,
                                                boolean glob) {
        String path = PATH_KEYS.stream().map(arguments::get).filter(Objects::nonNull).map(Object::toString)
                .findFirst().orElse(".");
        if (!inside(read, here, path)) {
            return Optional.of("jagt refuses reading " + path + ": a read stays inside the round's worktrees.");
        }
        Object pattern = arguments.get("pattern");
        if (glob && pattern != null && !globInside(read, here, pattern.toString())) {
            return Optional.of("jagt refuses the pattern " + pattern + ": a read stays inside the round's worktrees.");
        }
        return Optional.empty();
    }

    private static boolean globInside(ReadScope read, Path here, String pattern) {
        if (PARENT.matcher(pattern).find() || pattern.startsWith("~")) {
            return false;
        }
        return !pattern.startsWith("/") || inside(read, here, pattern.split("[*?\\[{]", 2)[0]);
    }

    static boolean inside(ReadScope read, Path here, String path) {
        if (path.startsWith("~")) {
            return false;
        }
        Path target = real(here.resolve(path));
        return read.roots().stream().map(ReadGate::real).anyMatch(target::startsWith);
    }

    /** A link inside a worktree may point anywhere, so a path is judged where it really leads. */
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
