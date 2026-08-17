package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskRepo;
import dev.jagt.orchestrator.platform.UserNotifier;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Finds worktree directories that no task owns any more, and REPORTS them — it never deletes anything.
 *
 * <p>Why it matters beyond tidiness: {@code worktree.copyGlobs} deliberately copies gitignored local files
 * ({@code .env}, keys, certs) into every worktree so the app can run there, and those copies are removed only
 * when {@code done} succeeds in deleting the directory. {@code removeWorktree} is best-effort and a crashed or
 * abandoned run leaves the whole tree — credentials included — sitting next to the repo indefinitely. So each
 * orphan is reported together with how many of those copied secret files it still holds.
 *
 * <p>Deleting is the human's call: an orphan can also hold uncommitted work, which is exactly why `done`
 * keeps branches and why this only ever looks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorktreeOrphanScanner {

    /** Directories never worth walking for secret copies (huge and/or generated). */
    private static final Set<String> SKIP = Set.of(".git", "node_modules", "build", "target", "out", "dist",
            ".gradle", ".idea");

    /** One leftover directory: where it is, which project's naming it follows, and what it still holds. */
    public record Orphan(Path path, String projectKey, int secretFiles) {
    }

    private final ConfigService configService;
    private final StateService stateService;
    private final UserNotifier userNotifier;

    /**
     * One ping at startup when something is rotting, because a log line alone would be invisible: the Master
     * TUI takes over the screen the moment it starts. The details live in {@link #report()}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reportOnStartup() {
        List<Orphan> orphans;
        try {
            orphans = scan();
        } catch (RuntimeException e) {
            // A DIAGNOSTIC must never be able to stop the backend from starting: an ApplicationReadyEvent
            // listener that throws fails the whole boot. Reading the config is exactly such a risk — it
            // refuses to load when config.json is missing.
            log.warn("Could not scan for orphaned worktrees: {}", e.getMessage());
            return;
        }
        if (orphans.isEmpty()) {
            return;
        }
        int secrets = orphans.stream().mapToInt(Orphan::secretFiles).sum();
        orphans.forEach(orphan -> log.warn("Orphaned worktree {} ({} copied secret file(s)) — no task owns it",
                orphan.path(), orphan.secretFiles()));
        userNotifier.notify("jagt · " + orphans.size() + " orphaned worktree(s)",
                secrets > 0 ? secrets + " copied secret file(s) left on disk — see /orphans"
                        : "left over from a crashed or abandoned task — see /orphans");
    }

    /** Every leftover worktree directory across all configured projects, deduplicated by path. */
    public List<Orphan> scan() {
        Map<Path, Orphan> found = new LinkedHashMap<>();
        Set<String> owned = ownedDirectoryNames();
        ConfigService.ConfigFile config = configService.load();
        List<PathMatcher> secretMatchers = WorktreeFiles.localFileMatchers(
                config.worktree().copyGlobsOrDefault());
        config.projects().forEach((projectKey, project) -> {
            Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
            Path parent = projectPath.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return;
            }
            for (String name : orphanNames(directoryNames(parent), projectKey, owned)) {
                Path path = parent.resolve(name);
                if (!path.equals(projectPath)) {
                    found.putIfAbsent(path, new Orphan(path, projectKey,
                            countSecretFiles(path, secretMatchers)));
                }
            }
        });
        return List.copyOf(found.values());
    }

    public String report() {
        List<Orphan> orphans = scan();
        if (orphans.isEmpty()) {
            return "no orphaned worktrees — every directory on disk belongs to a task in state.json.\n";
        }
        StringBuilder out = new StringBuilder("orphaned worktrees — directories no task owns."
                + " jagt never deletes these:\n\n");
        orphans.forEach(orphan -> out.append(String.format("  %-60s %-10s %s%n", orphan.path(),
                orphan.projectKey(),
                orphan.secretFiles() == 0 ? "-" : orphan.secretFiles() + " copied secret file(s)")));
        return out + "\nDelete them yourself once you are sure: an orphan can still hold uncommitted work,"
                + " and the secret copies came from this project's worktree.copyGlobs.\n";
    }

    /**
     * Which directory names look like jagt worktrees for this project but belong to no live task:
     * {@code <taskId>-<projectKey>} (a task worktree) or {@code <taskId>-deploy} (a conflicted deploy that was
     * never finished). Pure, so the naming rule is testable without a filesystem.
     */
    static Set<String> orphanNames(List<String> directoryNames, String projectKey, Set<String> ownedNames) {
        return directoryNames.stream()
                .filter(name -> name.endsWith("-" + projectKey) || name.endsWith("-deploy"))
                .filter(name -> !ownedNames.contains(name))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    /**
     * Directory names a live task owns: EVERY repository's worktree — a task spanning two projects has two, and
     * naming only the first would report the other as rotting while its agent is editing it — plus the deploy
     * worktree a conflict may have left.
     */
    private Set<String> ownedDirectoryNames() {
        Set<String> owned = new java.util.HashSet<>();
        stateService.tasks().forEach((taskId, task) -> {
            owned.add(taskId + "-deploy");
            task.repos().stream()
                    .map(TaskRepo::worktreePath)
                    .filter(path -> path != null && !path.isBlank())
                    .map(path -> Path.of(path).getFileName())
                    .filter(java.util.Objects::nonNull)
                    .forEach(name -> owned.add(name.toString()));
        });
        return owned;
    }

    private static List<String> directoryNames(Path parent) {
        try (Stream<Path> entries = Files.list(parent)) {
            List<String> names = new ArrayList<>();
            entries.filter(Files::isDirectory).forEach(dir -> names.add(dir.getFileName().toString()));
            return names;
        } catch (IOException e) {
            log.warn("Could not list {} for orphaned worktrees: {}", parent, e.getMessage());
            return List.of();
        }
    }

    /** How many of the files jagt copies into a worktree (secrets, keys, certs) are still sitting in there. */
    private static int countSecretFiles(Path worktree, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) {
            return 0;
        }
        int[] hits = {0};
        try {
            Files.walkFileTree(worktree, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return SKIP.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = worktree.relativize(file);
                    if (matchers.stream().anyMatch(matcher -> matcher.matches(relative))) {
                        hits[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan {} for copied secrets: {}", worktree, e.getMessage());
        }
        return hits[0];
    }
}
