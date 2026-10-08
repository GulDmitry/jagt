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
    private static final Set<String> GIT_READS = Set.of("diff", "log", "show", "status", "blame", "merge-base",
            "rev-parse", "ls-files");
    /** Each writes a file, or reads one from outside the repository; git takes any unambiguous prefix. */
    private static final List<String> GIT_REFUSED = List.of("--output", "--no-index", "--contents", "--ext-diff");
    private static final Pattern SHELL_SYNTAX = Pattern.compile("[;&|<>$`()\\n\\\\]");
    private static final Pattern PARENT = Pattern.compile("(^|/)\\.\\.(/|$)");

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
            return gitRefusal(read, String.valueOf(arguments.getOrDefault("command", "")), here);
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

    private static Optional<String> gitRefusal(ReadScope read, String command, Path here) {
        List<String> words = List.of(command.strip().split("\\s+"));
        if (SHELL_SYNTAX.matcher(command).find() || words.size() < 2 || !"git".equals(words.getFirst())) {
            return Optional.of("jagt refuses this command in a read: one read-only git command, nothing around it.");
        }
        int at = 1;
        Path repository = here;
        if ("-C".equals(words.get(at)) && words.size() > 3) {
            if (!inside(read, here, bare(words.get(at + 1)))) {
                return Optional.of("jagt refuses git outside the round's worktrees.");
            }
            repository = here.resolve(bare(words.get(at + 1)));
            at += 2;
        }
        if (!GIT_READS.contains(words.get(at))) {
            return Optional.of("jagt refuses git " + words.get(at) + " in a read: only " + GIT_READS + " run.");
        }
        for (String word : words.subList(at + 1, words.size())) {
            String argument = bare(word);
            String name = argument.split("=", 2)[0];
            if (name.startsWith("--") && name.length() > 3 && GIT_REFUSED.stream().anyMatch(o -> o.startsWith(name))) {
                return Optional.of("jagt refuses git " + name + " in a read: it writes or reads outside the repository.");
            }
            if ((argument.startsWith("/") || argument.startsWith("~") || PARENT.matcher(argument).find())
                    && !inside(read, repository, argument)) {
                return Optional.of("jagt refuses the path " + argument + ": a read stays inside the round's worktrees.");
            }
        }
        return Optional.empty();
    }

    private static String bare(String word) {
        return word.replaceAll("['\"]", "");
    }

    private static boolean inside(ReadScope read, Path here, String path) {
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
