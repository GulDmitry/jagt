package dev.jagt.orchestrator.service;


import dev.jagt.orchestrator.port.AgentRuntime;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** The files a fresh worktree needs that git does not carry, and jagt's own plumbing kept out of git status. */
@Slf4j
public final class WorktreeFiles {

    public static final String REVIEW_REPLIES = "review_replies.md";

    /** Directories never worth scanning for local files (huge and/or generated). */
    private static final Set<String> COPY_SCAN_SKIP =
            Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle", ".idea");

    private static final List<String> GENERATED = List.of(".jagt", "task_context.md", REVIEW_REPLIES);

    private WorktreeFiles() {
    }

    /**
     * What jagt writes into a worktree whatever the checkout already holds, the active runtime's own files
     * included. The copy is that worktree's, not the project's, so a commit must not carry it back.
     */
    public static List<String> generated(AgentRuntime runtime) {
        return Stream.concat(GENERATED.stream(), runtime.generatedFiles().stream()).distinct().toList();
    }

    /**
     * Keeps orchestrator plumbing out of `git status` in every worktree of the project. info/exclude only
     * affects untracked files, so a project's own tracked AGENTS.md/CLAUDE.md is unaffected.
     */
    public static void excludeOrchestratorPlumbing(Path gitCommonDir, AgentRuntime runtime) {
        List<String> entries = Stream.concat(
                Stream.of("mcp_client.js", AgentRuntime.SYSTEM_KNOWLEDGE_FILE, "task_context.md",
                        REVIEW_REPLIES, ".jagt/", ".run/"),
                runtime.statusExclusions().stream()).distinct().toList();
        try {
            Path exclude = gitCommonDir.resolve("info").resolve("exclude");
            Files.createDirectories(exclude.getParent());
            String current = Files.exists(exclude) ? Files.readString(exclude) : "";
            StringBuilder additions = new StringBuilder();
            for (String entry : entries) {
                if (current.lines().noneMatch(entry::equals)) {
                    additions.append(entry).append('\n');
                }
            }
            if (!additions.isEmpty()) {
                Files.writeString(exclude, current.isEmpty() || current.endsWith("\n")
                        ? current + additions
                        : current + "\n" + additions);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update git info/exclude in " + gitCommonDir, e);
        }
    }

    /**
     * The IDE's own files are gitignored in the base repository, so a fresh checkout of the task branch lacks
     * them. Best-effort; an absent path is a no-op.
     */
    public static void copyIdeProjectFiles(Path projectPath, Path worktreePath) {
        List<String> ideFiles = List.of(".run", ".idea/runConfigurations",
                ".idea/dataSources.xml", ".idea/dataSources.local.xml", ".idea/dataSources");
        for (String path : ideFiles) {
            copyTree(projectPath.resolve(path), worktreePath.resolve(path), worktreePath);
        }
    }

    /** Gitignored local files the run configs reference but git omits. Best-effort; heavy directories skipped. */
    public static void copyLocalFiles(Path projectPath, Path worktreePath, List<String> globs) {
        var matchers = localFileMatchers(globs);
        if (matchers.isEmpty()) {
            return;
        }
        try {
            Files.walkFileTree(projectPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return COPY_SCAN_SKIP.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = projectPath.relativize(file);
                    Path target = worktreePath.resolve(relative);
                    // Anything already in a fresh worktree came out of the checkout, so git TRACKS it and this
                    // is not the file the app is missing; overwriting starts the worktree dirty.
                    if (!Files.exists(target) && matchers.stream().anyMatch(m -> m.matches(relative))) {
                        copyFile(file, target);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.atWarn().setMessage("local file scan failed")
                    .addKeyValue("path", projectPath)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
    }

    /**
     * The patterns as matchers against a repository-relative path. A {@code **}{@code /} prefix ALSO matches at the
     * root: Java's glob wants a directory component there, so "any .env" would skip a single-module repository's.
     */
    public static List<PathMatcher> localFileMatchers(List<String> globs) {
        return (globs == null ? List.<String>of() : globs).stream()
                .filter(glob -> glob != null && !glob.isBlank())
                .map(String::strip)
                .flatMap(glob -> glob.startsWith("**/")
                        ? Stream.of(glob, glob.substring("**/".length()))
                        : Stream.of(glob))
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
                .toList();
    }

    /** The file's content, or empty when it is not there — an absent relay file is the normal first case. */
    public static java.util.Optional<String> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Files.readString(file));
        } catch (IOException e) {
            log.atWarn().setMessage("file read failed")
                    .addKeyValue("path", file)
                    .addKeyValue("cause", e.getMessage())
                    .log();
            return java.util.Optional.empty();
        }
    }

    /** Fails loudly: unlike a read, a write that did not happen is not something to shrug at. */
    public static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static void copyTree(Path source, Path target, Path worktreePath) {
        if (Files.isRegularFile(source)) {
            copyFile(source, target);
            return;
        }
        if (!Files.isDirectory(source)) {
            return;
        }
        try (var files = Files.walk(source)) {
            files.forEach(from -> {
                Path to = target.resolve(source.relativize(from));
                if (Files.isDirectory(from)) {
                    mkdirs(to);
                } else {
                    copyFile(from, to);
                }
            });
        } catch (IOException e) {
            log.atWarn().setMessage("file copy failed")
                    .addKeyValue("from", source)
                    .addKeyValue("to", worktreePath)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
    }

    private static void copyFile(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.atWarn().setMessage("file copy failed")
                    .addKeyValue("from", from)
                    .addKeyValue("to", to)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
    }

    private static void mkdirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.atWarn().setMessage("directory create failed")
                    .addKeyValue("path", dir)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
    }
}
