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
    private static final Pattern PROJECTS = Pattern.compile("(?m)^( +)projects:[ \\t]*(\\{\\s*})?[ \\t]*$");

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
        var projects = configService.load().projects();
        boolean taken = projects.values().stream().anyMatch(project -> Path.of(expand(project.path()))
                .toAbsolutePath().normalize().equals(path));
        lines.add(new Line(!taken, taken ? "a project already points here" : "no project points here yet"));
        if (projects.containsKey(key)) {
            lines.add(new Line(false, "the key " + key + " is taken: pick another"));
        }
        List<String> verify = Files.exists(path.resolve("gradlew")) ? List.of("./gradlew", "test")
                : Files.exists(path.resolve("package.json")) ? List.of("npm", "test") : List.of();
        return new Reading(lines, key, base, deploy, verify);
    }

    /** Refuses an entry the file could not hold, and leaves the file as it was when the result does not read. */
    public String add(Entry entry) {
        if (entry.key() == null || !KEY.matcher(entry.key()).matches()) {
            throw new IllegalArgumentException("A key starts with a letter and holds letters, digits, - or _");
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
        try {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            Matcher projects = PROJECTS.matcher(before);
            if (!projects.find()) {
                throw new IllegalArgumentException("jagt.yml holds no projects: section to add to");
            }
            String indent = projects.group(1) + "  ";
            String block = block(indent, entry, Path.of(expand(entry.path())).toAbsolutePath().normalize());
            String rest = before.substring(projects.end());
            String after = before.substring(0, projects.start()) + projects.group(1) + "projects:\n" + block
                    + (rest.startsWith("\n") ? rest.substring(1) : rest);
            Path written = Files.writeString(file.resolveSibling(file.getFileName() + ".tmp"), after,
                    StandardCharsets.UTF_8);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            try {
                configService.project(entry.key());
            } catch (RuntimeException unreadable) {
                Files.writeString(file, before, StandardCharsets.UTF_8);
                throw new IllegalStateException("jagt.yml did not read after the change, so it was put back: "
                        + unreadable.getMessage(), unreadable);
            }
            return "Added " + entry.key() + " to jagt.yml";
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private String block(String indent, Entry entry, Path path) {
        StringBuilder block = new StringBuilder();
        String inner = indent + "  ";
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
        return path == null ? "" : path.strip().startsWith("~/") ? home + path.strip().substring(1) : path.strip();
    }
}
