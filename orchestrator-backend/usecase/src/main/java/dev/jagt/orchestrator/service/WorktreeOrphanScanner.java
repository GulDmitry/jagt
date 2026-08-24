package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.job.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
 * WARNS about worktree directories no task owns and deletes nothing: an orphan can hold uncommitted work AND
 * copies of gitignored secrets, so what becomes of it is the human's call. No surface offers it either —
 * housekeeping is not something a human acts on mid-flight.
 *
 * <p>Each orphan is reported with how many of those copied files it still holds, because a copy goes only when
 * its directory does, and removing a directory is best-effort.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorktreeOrphanScanner implements Job {
    @Override
    public String id() {
        return "orphanscan";
    }

    @Override
    public String describe() {
        return "warn about worktree directories no task owns, which can hold work and copied secrets";
    }

    @Override
    public Duration every() {
        return null;
    }


    /** Directories never worth walking for secret copies (huge and/or generated). */
    private static final Set<String> SKIP = Set.of(".git", "node_modules", "build", "target", "out", "dist",
            ".gradle", ".idea");

    public record Orphan(Path path, String projectKey, int secretFiles) {
    }

    private final ConfigService configService;
    private final StateService stateService;
    private final Notifications notifications;

    /**
     * One WARN per leftover directory, plus a single desktop ping: the log carries the detail, and the ping is
     * for whoever never opens it — the Master TUI takes over the screen the moment it starts.
     */
    @Override
    public void run() {
        List<Orphan> orphans = scan();
        if (orphans.isEmpty()) {
            return;
        }
        int secrets = orphans.stream().mapToInt(Orphan::secretFiles).sum();
        orphans.forEach(orphan -> log.atWarn().setMessage("worktree orphaned")
                .addKeyValue("path", orphan.path())
                .addKeyValue("secrets", orphan.secretFiles())
                .addKeyValue("owner", "none")
                .log());
        notifications.send(Notification.housekeeping(orphans.size() + " orphaned worktree(s)",
                secrets > 0 ? secrets + " copied secret file(s) left on disk — see the log"
                        : "left over from a crashed or abandoned task — see the log"));
    }

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
            for (String name : orphanNames(directoryNames(parent), projectKey, owned,
                    name -> holdsCheckout(parent.resolve(name)))) {
                Path path = parent.resolve(name);
                if (!path.equals(projectPath)) {
                    found.putIfAbsent(path, new Orphan(path, projectKey,
                            countSecretFiles(path, secretMatchers)));
                }
            }
        });
        return List.copyOf(found.values());
    }

    /**
     * A {@code -deploy} or {@code -revert} suffix is a round nobody finished, and a leftover just the same.
     * Naming a DEPLOY directory after a live task protects a real checkout only: what is left once git removed
     * the worktree is nobody's, whatever that task is doing.
     */
    static Set<String> orphanNames(List<String> directoryNames, String projectKey, Set<String> ownedNames,
                                   java.util.function.Predicate<String> holdsCheckout) {
        return directoryNames.stream()
                .filter(name -> name.endsWith("-" + projectKey) || name.endsWith("-deploy")
                        || name.endsWith("-revert"))
                .filter(name -> !ownedNames.contains(name)
                        || (name.endsWith("-deploy") && !holdsCheckout.test(name)))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    /**
     * A linked worktree's {@code .git} is a POINTER, and it outlives the registration it names: a removal that
     * unregistered the worktree and then failed to delete the directory leaves one behind, checkout and copied
     * secrets and all — which is the leftover worth reporting, not the one to keep quiet about.
     */
    private static boolean holdsCheckout(Path directory) {
        Path marker = directory.resolve(".git");
        if (Files.isDirectory(marker)) {
            return true;
        }
        try {
            String pointer = Files.readString(marker).strip();
            return pointer.startsWith("gitdir:")
                    && Files.exists(directory.resolve(pointer.substring("gitdir:".length()).strip()));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Directory names a live task owns: EVERY repository's worktree — a task spanning two projects has two, and
     * naming only the first would report the other as rotting while its agent is editing it — plus the deploy
     * worktree a conflict may have left.
     */
    private Set<String> ownedDirectoryNames() {
        Set<String> owned = new java.util.HashSet<>();
        stateService.tasks().forEach((taskId, task) -> {
            owned.add(TaskName.slug(taskId) + "-deploy");
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
            log.atWarn().setMessage("worktree scan failed")
                    .addKeyValue("path", parent)
                    .addKeyValue("cause", e.getMessage())
                    .log();
            return List.of();
        }
    }

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
            log.atWarn().setMessage("secret scan failed")
                    .addKeyValue("path", worktree)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
        return hits[0];
    }
}
