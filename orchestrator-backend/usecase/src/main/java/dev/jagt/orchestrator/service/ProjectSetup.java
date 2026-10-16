package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.Processes;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A repository read before it becomes a project, and its entry written into the configuration file the human
 * keeps. The file stays theirs: one block is inserted under {@code projects:}, every other line is left as it was,
 * and a file that no longer reads afterwards is put back.
 */
@Service
public class ProjectSetup {

    private static final Duration GIT = Duration.ofSeconds(10);
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,39}");
    private static final Pattern ROOT = Pattern.compile("(?m)^orchestrator:[ \\t]*(#.*)?$");
    private static final Pattern LEADING = Pattern.compile("^( *)\\S");

    public record Line(boolean ok, String text) {
    }

    public record Reading(List<Line> lines, String key, String baseBranch, String deployBranch,
                          List<String> verifyCommand) {

        public boolean ready() {
            return lines.stream().allMatch(Line::ok);
        }
    }

    public record Entry(String key, String path, String baseBranch, @Nullable String deployBranch,
                        @Nullable List<String> verifyCommand, @Nullable String about) {
    }

    private final ConfigService configService;
    private final OrchestratorPaths paths;
    private final Processes processes;
    private final JsonMapper json = new JsonMapper();

    public ProjectSetup(ConfigService configService, OrchestratorPaths paths, Processes processes) {
        this.configService = configService;
        this.paths = paths;
        this.processes = processes;
    }

    /** What the path is, and what its entry would most likely say. Writes nothing. */
    public Reading read(String rawPath) {
        Path path = Path.of(expand(rawPath)).toAbsolutePath().normalize();
        List<Line> lines = new ArrayList<>();
        String key = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!Files.isDirectory(path)) {
            lines.add(new Line(false, "no directory at " + path));
            return new Reading(lines, key, "", "", List.of());
        }
        if (git(path, "rev-parse", "--show-toplevel").isEmpty()) {
            lines.add(new Line(false, "not a git repository"));
            return new Reading(lines, key, "", "", List.of());
        }
        String origin = git(path, "remote", "get-url", "origin").orElse("");
        lines.add(new Line(!origin.isBlank(), origin.isBlank() ? "no remote named origin" : "a git repository, origin " + origin));
        String base = git(path, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
                .orElse(exists(path, "origin/main") ? "origin/main" : exists(path, "origin/master") ? "origin/master" : "");
        lines.add(new Line(!base.isBlank(), base.isBlank() ? "no origin/main or origin/master to cut tasks from"
                : "tasks are cut from " + base));
        String deploy = exists(path, "origin/dev") ? "dev" : "";
        Path real = real(path);
        boolean taken = configService.load().projects().values().stream()
                .anyMatch(project -> real(Path.of(expand(project.path())).toAbsolutePath().normalize()).equals(real));
        lines.add(new Line(!taken, taken ? "a project already points here" : "no project points here yet"));
        List<String> verify = Files.exists(path.resolve("gradlew")) ? List.of("./gradlew", "test")
                : Files.exists(path.resolve("package.json")) ? List.of("npm", "test") : List.of();
        return new Reading(lines, key, base, deploy, verify);
    }

    /** Refuses an entry the file could not hold, and leaves the file as it was when the result does not read. */
    public synchronized String add(Entry entry) {
        if (entry.key() == null || !KEY.matcher(entry.key()).matches()) {
            throw new IllegalArgumentException("A key starts with a letter and holds letters, digits, - or _");
        }
        if (configService.load().projects().containsKey(entry.key())) {
            throw new IllegalArgumentException("The key " + entry.key() + " is taken: pick another");
        }
        Reading reading = read(entry.path());
        if (!reading.ready()) {
            throw new IllegalArgumentException(reading.lines().stream().filter(line -> !line.ok())
                    .map(Line::text).findFirst().orElse("the path is not ready"));
        }
        if (entry.baseBranch() == null || entry.baseBranch().isBlank()) {
            throw new IllegalArgumentException("A project names the branch tasks are cut from");
        }
        Path file = paths.configFile();
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            Files.move(Files.writeString(temp, inserted(before, entry), StandardCharsets.UTF_8), file,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            try {
                configService.project(entry.key());
            } catch (RuntimeException unreadable) {
                Files.move(Files.writeString(temp, before, StandardCharsets.UTF_8), file,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                throw new IllegalStateException("jagt.yml did not read after the change, so it was put back: "
                        + unreadable.getMessage(), unreadable);
            }
            return "Added " + entry.key() + " to jagt.yml";
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot change " + file + ": " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // A left-over temporary file is overwritten by the next add.
            }
        }
    }

    /** The file with the entry first under the {@code projects:} that is a direct child of {@code orchestrator:}. */
    private String inserted(String file, Entry entry) {
        Matcher root = ROOT.matcher(file);
        if (!root.find()) {
            throw new IllegalArgumentException("jagt.yml holds no orchestrator: section");
        }
        String[] lines = file.substring(root.end()).split("\n", -1);
        String child = null;
        int at = -1;
        for (int i = 1; i < lines.length; i++) {
            String indent = indent(lines[i]);
            if (indent == null) continue;
            if (indent.isEmpty()) break;
            if (child == null) child = indent;
            if (indent.equals(child) && lines[i].strip().matches("projects:\\s*(\\{\\s*})?\\s*(#.*)?")) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            throw new IllegalArgumentException("jagt.yml holds no projects: under orchestrator: to add to");
        }
        String parent = java.util.Objects.requireNonNull(child);
        String entryIndent = parent + parent;
        for (int i = at + 1; i < lines.length; i++) {
            String indent = indent(lines[i]);
            if (indent == null) continue;
            if (indent.length() > parent.length()) entryIndent = indent;
            break;
        }
        lines[at] = parent + "projects:";
        String block = block(entryIndent, entry, Path.of(expand(entry.path())).toAbsolutePath().normalize(),
                entryIndent.substring(parent.length()));
        lines[at] = lines[at] + "\n" + block.substring(0, block.length() - 1);
        return file.substring(0, root.end()) + String.join("\n", lines);
    }

    /** The leading spaces of a line that holds a key, or null for a blank or comment line. */
    private static @Nullable String indent(String line) {
        Matcher leading = LEADING.matcher(line);
        return leading.find() && !line.strip().startsWith("#") ? leading.group(1) : null;
    }

    private String block(String indent, Entry entry, Path path, String step) {
        StringBuilder block = new StringBuilder();
        String inner = indent + step;
        block.append(indent).append(entry.key()).append(":\n");
        block.append(inner).append("path: ").append(json.writeValueAsString(path.toString())).append('\n');
        block.append(inner).append("baseBranch: ").append(json.writeValueAsString(entry.baseBranch())).append('\n');
        if (entry.deployBranch() != null && !entry.deployBranch().isBlank()) {
            block.append(inner).append("deployBranch: ").append(json.writeValueAsString(entry.deployBranch())).append('\n');
        }
        if (entry.verifyCommand() != null && !entry.verifyCommand().isEmpty()) {
            block.append(inner).append("verifyCommand: ").append(json.writeValueAsString(entry.verifyCommand())).append('\n');
        }
        if (entry.about() != null && !entry.about().isBlank()) {
            block.append(inner).append("about: ").append(json.writeValueAsString(entry.about())).append('\n');
        }
        return block.toString();
    }

    private java.util.Optional<String> git(Path path, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", path.toString()));
        command.addAll(List.of(args));
        Processes.Result result = processes.run(null, GIT, command);
        return result.exitCode() == 0 ? java.util.Optional.of(result.stdout().strip()) : java.util.Optional.empty();
    }

    private boolean exists(Path path, String ref) {
        return git(path, "rev-parse", "--verify", "--quiet", ref).isPresent();
    }

    private static String expand(String path) {
        String home = System.getProperty("user.home");
        String typed = path == null ? "" : path.strip();
        return typed.equals("~") || typed.startsWith("~/") ? home + typed.substring(1) : typed;
    }

    /** Where a path really leads, so a link to a configured repository is the same repository. */
    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }
}
