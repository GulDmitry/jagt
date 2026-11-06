package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AtomicFiles;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ProjectProblems;
import dev.jagt.orchestrator.port.BuildTools;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * A repository read before it becomes a project, and its entry written into the configuration file the human
 * keeps. The file stays theirs: one block is inserted, changed or deleted under {@code projects:}, every other
 * line is left as it was, and a file that no longer reads afterwards is put back.
 */
@Service
@RequiredArgsConstructor
public class ProjectSetup {

    private static final Duration GIT = Duration.ofSeconds(10);
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,39}");
    private static final List<String> DEPLOY_NAMES = List.of("dev", "develop", "staging");

    public record Line(boolean ok, String text) {
    }

    public record Reading(List<Line> lines, String key, String baseBranch, String deployBranch,
                          List<String> deployBranches, List<String> verifyCommand) {

        public boolean ready() {
            return lines.stream().allMatch(Line::ok);
        }

        public String firstProblem() {
            return lines.stream().filter(line -> !line.ok()).map(Line::text).findFirst().orElse("the path is not ready");
        }
    }

    public record Entry(String key, String path, String baseBranch, @Nullable String deployBranch,
                        @Nullable List<String> verifyCommand, @Nullable String about,
                        @Nullable List<String> labels, @Nullable List<String> humanPaths) {
    }

    private final ConfigService configService;
    private final OrchestratorPaths paths;
    private final Processes processes;
    private final BuildTools buildTools;

    /** What the path is, and what its entry would most likely say. Writes nothing. */
    public Reading read(String rawPath) {
        return read(rawPath, null);
    }

    /** The same for the project {@code own}, which does not count as another one pointing at the path. */
    public Reading read(String rawPath, @Nullable String own) {
        Path path = Path.of(expand(rawPath)).toAbsolutePath().normalize();
        String key = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        Optional<String> notARepositoryRoot = problemWith(path);
        if (notARepositoryRoot.isPresent()) {
            return new Reading(List.of(new Line(false, notARepositoryRoot.get())), key, "", "", List.of(), List.of());
        }
        List<Line> lines = new ArrayList<>();
        lines.add(remoteLine(path));
        String base = baseBranch(path);
        lines.add(new Line(!base.isBlank(), base.isBlank() ? "no origin/main or origin/master to cut tasks from"
                : "tasks are cut from " + base));
        List<String> offered = deployBranches(path, base);
        String deploy = DEPLOY_NAMES.stream().filter(offered::contains).findFirst().orElse("");
        boolean taken = taken(path, own);
        lines.add(new Line(!taken, taken ? "a project already points here" : "no project points here yet"));
        return new Reading(lines, key, base, deploy, offered, buildTools.testCommand(path));
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

    private boolean taken(Path path, @Nullable String own) {
        Path real = real(path);
        return configService.load().projects().entrySet().stream().filter(project -> !project.getKey().equals(own))
                .anyMatch(project -> real(Path.of(expand(project.getValue().path())).toAbsolutePath().normalize())
                        .equals(real));
    }

    /** The remote's branches as last fetched, without its head and without the base tasks are cut from. */
    private List<String> deployBranches(Path path, String base) {
        String prefix = "origin/";
        return gitOut(path, "for-each-ref", "--format=%(refname:short)", "refs/remotes/origin").stream()
                .flatMap(String::lines)
                .map(String::strip)
                .filter(ref -> ref.startsWith(prefix) && !ref.equals(prefix + "HEAD") && !ref.equals(base))
                .map(ref -> ref.substring(prefix.length()))
                .toList();
    }

    /** Refuses an entry the file could not hold, and leaves the file as it was when the result does not read. */
    public synchronized String add(Entry entry) {
        if (entry.key() == null || !KEY.matcher(entry.key()).matches()) {
            throw new IllegalArgumentException("A key starts with a letter and holds letters, digits, - or _");
        }
        if (configService.load().projects().containsKey(entry.key())) {
            throw new IllegalArgumentException("The key " + entry.key() + " is taken: pick another");
        }
        check(entry);
        return rewrite(entry.key(), file -> ProjectBlocks.inserted(file, entry, normalized(entry)),
                "Added " + entry.key() + " to jagt.yml");
    }

    /** What jagt.yml holds for one project, as the edit form starts from it. */
    public Entry entry(String key) {
        ProjectConfig project = configService.load().projects().get(key);
        if (project == null) {
            throw new IllegalArgumentException("No project " + key + " in jagt.yml");
        }
        return new Entry(key, project.path(), project.baseBranch(),
                project.deployBranch() == null || project.deployBranch().isBlank() ? null : project.deployBranch(),
                project.verifyCommand() == null || project.verifyCommand().isEmpty() ? null : project.verifyCommand(),
                project.about(), project.labels() == null || project.labels().isEmpty() ? null : project.labels(),
                project.humanPaths() == null || project.humanPaths().isEmpty() ? null : project.humanPaths());
    }

    /** Rewrites the managed keys of a project's block, leaving its other keys, comments and every other line. */
    public synchronized String edit(Entry entry) {
        if (!configService.load().projects().containsKey(entry.key())) {
            throw new IllegalArgumentException("No project " + entry.key() + " in jagt.yml to change");
        }
        check(entry);
        return rewrite(entry.key(), file -> ProjectBlocks.replaced(file, entry, normalized(entry)),
                "Changed " + entry.key() + " in jagt.yml");
    }

    /** Deletes a project's block; the last project stays, since jagt does not start without one. */
    public synchronized String remove(String key) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        if (!projects.containsKey(key)) {
            throw new IllegalArgumentException("No project " + key + " in jagt.yml to remove");
        }
        if (projects.size() == 1) {
            throw new IllegalArgumentException(key + " is the only project, and jagt does not start without one");
        }
        return rewrite(null, file -> ProjectBlocks.removed(file, key), "Removed " + key + " from jagt.yml");
    }

    private static Path normalized(Entry entry) {
        return Path.of(expand(entry.path())).toAbsolutePath().normalize();
    }

    private void check(Entry entry) {
        Reading reading = read(entry.path(), entry.key());
        if (!reading.ready()) {
            throw new IllegalArgumentException(reading.firstProblem());
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

    /** {@code readBack} is the project the result must still hold, or null to read the file whole. */
    private String rewrite(@Nullable String readBack, UnaryOperator<String> change, String done) {
        Path file = real(paths.configFile());
        try {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            AtomicFiles.write(file, change.apply(before).getBytes(StandardCharsets.UTF_8));
            try {
                if (readBack == null) {
                    configService.load();
                } else {
                    configService.project(readBack);
                }
            } catch (RuntimeException unreadable) {
                putBack(file, before, unreadable);
            }
            return done;
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

    static String expand(String path) {
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
