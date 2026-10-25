package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AtomicFiles;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ProjectProblems;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
        String key = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        Optional<String> notARepositoryRoot = problemWith(path);
        if (notARepositoryRoot.isPresent()) {
            return new Reading(List.of(new Line(false, notARepositoryRoot.get())), key, "", "", List.of());
        }
        List<Line> lines = new ArrayList<>();
        lines.add(remoteLine(path));
        String base = baseBranch(path);
        lines.add(new Line(!base.isBlank(), base.isBlank() ? "no origin/main or origin/master to cut tasks from"
                : "tasks are cut from " + base));
        String deploy = exists(path, "origin/dev") ? "dev" : "";
        boolean taken = taken(path);
        lines.add(new Line(!taken, taken ? "a project already points here" : "no project points here yet"));
        return new Reading(lines, key, base, deploy, verifyCommand(path));
    }

    private Optional<String> problemWith(Path path) {
        if (!Files.isDirectory(path)) {
            return Optional.of("no directory at " + path);
        }
        Processes.Result top = git(path, "rev-parse", "--show-toplevel");
        if (top.exitCode() != 0) {
            boolean notARepository = top.exitCode() == 128 && top.stderr().contains("not a git repository");
            return Optional.of(notARepository ? "not a git repository"
                    : "git could not read " + path + ": " + firstLine(top.stderr()));
        }
        String toplevel = top.stdout().strip();
        return real(Path.of(toplevel)).equals(real(path)) ? Optional.empty()
                : Optional.of("a folder inside the repository at " + toplevel + " — add that instead");
    }

    private Line remoteLine(Path path) {
        Processes.Result remote = git(path, "remote", "get-url", "origin");
        String origin = remote.stdout().strip();
        if (remote.exitCode() == 0 && !origin.isBlank()) {
            return new Line(true, "a git repository, origin " + GitRemote.withoutCredentials(origin));
        }
        if (remote.exitCode() == 0 || remote.exitCode() == 2 && remote.stderr().contains("No such remote")) {
            return new Line(false, "no remote named origin");
        }
        return new Line(false, "git could not read the remote of " + path + ": " + firstLine(remote.stderr()));
    }

    private String baseBranch(Path path) {
        return gitOut(path, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
                .orElse(exists(path, "origin/main") ? "origin/main" : exists(path, "origin/master") ? "origin/master" : "");
    }

    private boolean taken(Path path) {
        Path real = real(path);
        return configService.load().projects().values().stream()
                .anyMatch(project -> real(Path.of(expand(project.path())).toAbsolutePath().normalize()).equals(real));
    }

    private static List<String> verifyCommand(Path path) {
        if (Files.exists(path.resolve("gradlew"))) {
            return List.of("./gradlew", "test");
        }
        return Files.exists(path.resolve("package.json")) ? List.of("npm", "test") : List.of();
    }

    /** Refuses an entry the file could not hold, and leaves the file as it was when the result does not read. */
    public synchronized String add(Entry entry) {
        check(entry);
        return write(entry);
    }

    private void check(Entry entry) {
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
        Path normalized = Path.of(expand(entry.path())).toAbsolutePath().normalize();
        List<Problem> refused = ProjectProblems.of(entry.key(), new ProjectConfig(normalized.toString(),
                entry.baseBranch(), entry.deployBranch() == null ? "" : entry.deployBranch(), List.of()));
        if (!refused.isEmpty()) {
            throw new IllegalArgumentException(refused.get(0).text());
        }
    }

    private String write(Entry entry) {
        Path file = real(paths.configFile());
        try {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            AtomicFiles.write(file, inserted(before, entry).getBytes(StandardCharsets.UTF_8));
            try {
                configService.project(entry.key());
            } catch (RuntimeException unreadable) {
                putBack(file, before, unreadable);
            }
            return "Added " + entry.key() + " to jagt.yml";
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot change " + file + ": " + e.getMessage(), e);
        }
    }

    private static void putBack(Path file, String before, RuntimeException unreadable) {
        try {
            AtomicFiles.write(file, before.getBytes(StandardCharsets.UTF_8));
        } catch (IOException cannotRestore) {
            throw new IllegalStateException(file.getFileName() + " did not read after the change and could"
                    + " not be put back; fix it by hand: " + cannotRestore.getMessage(), cannotRestore);
        }
        throw new IllegalStateException(file.getFileName() + " did not read after the change, so it was put back: "
                + unreadable.getMessage(), unreadable);
    }

    /** The file with the entry first under the {@code projects:} that is a direct child of {@code orchestrator:}. */
    private String inserted(String file, Entry entry) {
        Matcher root = ROOT.matcher(file);
        if (!root.find()) {
            throw new IllegalArgumentException("jagt.yml holds no orchestrator: section");
        }
        String[] lines = file.substring(root.end()).split("\n", -1);
        ProjectsLine found = projectsLine(lines);
        int at = found.at();
        String parent = found.parent();
        String entryIndent = entryIndent(lines, at, parent);
        lines[at] = parent + "projects:";
        String block = block(entryIndent, entry, Path.of(expand(entry.path())).toAbsolutePath().normalize(),
                entryIndent.substring(parent.length()));
        lines[at] = lines[at] + "\n" + block.substring(0, block.length() - 1);
        return file.substring(0, root.end()) + String.join("\n", lines);
    }

    private record ProjectsLine(int at, String parent) {
    }

    private static ProjectsLine projectsLine(String[] lines) {
        String child = null;
        for (int i = 1; i < lines.length; i++) {
            String indent = indent(lines[i]);
            if (indent == null) continue;
            if (indent.isEmpty()) break;
            if (child == null) child = indent;
            if (indent.equals(child) && lines[i].strip().matches("projects:\\s*(\\{\\s*})?\\s*(#.*)?")) {
                return new ProjectsLine(i, child);
            }
        }
        throw new IllegalArgumentException("jagt.yml holds no projects: under orchestrator: to add to");
    }

    private static String entryIndent(String[] lines, int at, String parent) {
        for (int i = at + 1; i < lines.length; i++) {
            String indent = indent(lines[i]);
            if (indent == null) continue;
            return indent.length() > parent.length() ? indent : parent + parent;
        }
        return parent + parent;
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

    private Processes.Result git(Path path, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", path.toString()));
        command.addAll(List.of(args));
        return processes.run(null, GIT, command);
    }

    private java.util.Optional<String> gitOut(Path path, String... args) {
        Processes.Result result = git(path, args);
        return result.exitCode() == 0 ? java.util.Optional.of(result.stdout().strip()) : java.util.Optional.empty();
    }

    private boolean exists(Path path, String ref) {
        return gitOut(path, "rev-parse", "--verify", "--quiet", ref).isPresent();
    }

    private static String firstLine(String text) {
        String stripped = text.strip();
        int end = stripped.indexOf('\n');
        return end < 0 ? stripped : stripped.substring(0, end);
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
