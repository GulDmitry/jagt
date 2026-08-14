package dev.jagt.orchestrator.service;


import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

/**
 * The files a fresh worktree needs that git does not carry: the IDE's run configs and database connections,
 * and the gitignored local files the app cannot start without. Plus keeping jagt's own plumbing out of every
 * worktree's {@code git status}.
 *
 * <p>Statics with no collaborators on purpose: filesystem work with no state and no policy, so its tests need
 * nothing either.
 */
@Slf4j
public final class WorktreeFiles {

    /** Directories never worth scanning for local files (huge and/or generated). */
    private static final Set<String> COPY_SCAN_SKIP =
            Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle", ".idea");

    private WorktreeFiles() {
    }

    /**
     * Keeps orchestrator plumbing out of `git status` in every worktree of the project. info/exclude only
     * affects untracked files, so a project's own tracked AGENTS.md/CLAUDE.md is unaffected.
     *
     * <p>Takes the git COMMON dir rather than a repository path: resolving that is git's business, not this
     * class's, and passing it in is what keeps a GitService dependency out of here.
     */
    public static void excludeOrchestratorPlumbing(Path gitCommonDir) {
        List<String> entries = List.of("mcp_client.js", ".mcp.json", "AGENTS.md", "CLAUDE.md",
                "task_context.md", "review_replies.md", ".claude/", ".codex/", ".run/");
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
     * Copies the base project's IDE files (run configurations, database connections) into the worktree so
     * {@code ide <ticket>} opens ready to run and query. They are gitignored in the base repo, so a fresh
     * checkout of the task branch lacks them. Best-effort; absent path = no-op.
     */
    public static void copyIdeProjectFiles(Path projectPath, Path worktreePath) {
        List<String> ideFiles = List.of(".run", ".idea/runConfigurations",
                ".idea/dataSources.xml", ".idea/dataSources.local.xml", ".idea/dataSources");
        for (String path : ideFiles) {
            copyTree(projectPath.resolve(path), worktreePath.resolve(path), worktreePath);
        }
    }

    /**
     * Copies gitignored LOCAL files matching the configured {@code worktree.copyGlobs} from the base repo to
     * the same relative path in a new worktree — module {@code .env}, key files, SSL certs etc. that the run
     * configs reference but git omits, so the app can start in the worktree. The patterns are config, NOT
     * hardcoded. Best-effort; heavy dirs skipped. (Secrets live only in the local, gitignored worktree.)
     */
    public static void copyLocalFiles(Path projectPath, Path worktreePath, List<String> globs) {
        var matchers = (globs == null ? List.<String>of() : globs).stream()
                .filter(glob -> glob != null && !glob.isBlank())
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob.strip()))
                .toList();
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
                    if (matchers.stream().anyMatch(matcher -> matcher.matches(relative))) {
                        copyFile(file, worktreePath.resolve(relative));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan {} for local files: {}", projectPath, e.getMessage());
        }
    }

    /** The file's content, or empty when it is not there — an absent relay file is the normal first case. */
    /** Announced only where it is actionable: the file survives until the agent deletes it. */
    public static boolean draftedReplies(dev.jagt.orchestrator.model.TaskState task) {
        if (task.status() != dev.jagt.orchestrator.model.TaskStatus.REVIEW_PENDING
                && task.status() != dev.jagt.orchestrator.model.TaskStatus.CI_FAILED) {
            return false;
        }
        String worktree = task.worktreePath();
        return worktree != null && !worktree.isBlank()
                && java.nio.file.Files.isRegularFile(Path.of(worktree).resolve("review_replies.md"));
    }

    public static java.util.Optional<String> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Files.readString(file));
        } catch (IOException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /** Writes a file, failing loudly: a missing task_context.md or agent config is not something to shrug at. */
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
            log.warn("Could not copy {} into {}: {}", source, worktreePath, e.getMessage());
        }
    }

    private static void copyFile(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not copy {} -> {}: {}", from, to, e.getMessage());
        }
    }

    private static void mkdirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Could not create {}: {}", dir, e.getMessage());
        }
    }
}
