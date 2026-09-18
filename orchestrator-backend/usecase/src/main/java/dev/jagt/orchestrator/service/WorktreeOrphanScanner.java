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
 * WARNS about worktree directories no task owns: an orphan can hold uncommitted work AND copies of gitignored
 * secrets. Each is reported with how many of those copies it still holds. The one it deletes instead of reporting
 * is jagt's own residue — a directory the IDE wrote back into after the checkout was already gone.
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
        return "delete jagt's own worktree residue, warn about the leftovers holding work or copied secrets";
    }

    @Override
    public Duration every() {
        return null;
    }


    /** Directories never worth walking for secret copies (huge and/or generated). */
    private static final Set<String> SKIP = Set.of(".git", "node_modules", "build", "target", "out", "dist",
            ".gradle", ".idea");

    /** What an IDE writes into a worktree by itself, so finding it says nothing about whose the directory is. */
    private static final Set<String> IDE_FILES = Set.of(".idea", ".run");

    public record Orphan(Path path, String projectKey, int secretFiles) {
    }

    private final ConfigService configService;
    private final StateService stateService;
    private final Notifications notifications;

    /** One WARN per leftover directory, plus a single desktop ping for whoever never opens the log. */
    @Override
    public void run() {
        List<Orphan> orphans = scan().stream().filter(orphan -> !sweep(orphan)).toList();
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

    /**
     * Deletes what jagt can prove is its own residue and nobody's work: no checkout, no copied secret, and every
     * file in it either the IDE's own or empty. The IDE rewrites its project files into a path git already
     * emptied, and the husk that leaves outlives the worktree by itself.
     */
    private static boolean sweep(Orphan orphan) {
        if (orphan.secretFiles() > 0 || Files.exists(orphan.path().resolve(".git"))
                || !holdsOnlyIdeFiles(orphan.path())) {
            return false;
        }
        try {
            Files.walkFileTree(orphan.path(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.atInfo().setMessage("worktree husk deleted")
                    .addKeyValue("path", orphan.path())
                    .log();
            return true;
        } catch (IOException e) {
            log.atWarn().setMessage("worktree husk delete failed")
                    .addKeyValue("path", orphan.path())
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "reported as an orphan instead")
                    .log();
            return false;
        }
    }

    private static boolean holdsOnlyIdeFiles(Path directory) {
        boolean[] onlyIdeFiles = {true};
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return IDE_FILES.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.size() == 0) {
                        return FileVisitResult.CONTINUE;
                    }
                    onlyIdeFiles[0] = false;
                    return FileVisitResult.TERMINATE;
                }
            });
        } catch (IOException e) {
            log.atWarn().setMessage("husk scan failed")
                    .addKeyValue("path", directory)
                    .addKeyValue("cause", e.toString())
                    .log();
            return false;
        }
        return onlyIdeFiles[0];
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
                if (!path.equals(projectPath) && !somebodyElses(name, path)) {
                    found.putIfAbsent(path, new Orphan(path, projectKey,
                            countSecretFiles(path, secretMatchers)));
                }
            }
        });
        return List.copyOf(found.values());
    }

    /**
     * A checkout under a name jagt could have cut but carrying no file of jagt's: a worktree somebody made by
     * hand, whose work is nobody's business here. Only the task's own name is ambiguous — a {@code -deploy} or
     * {@code -revert} one nothing but jagt ever writes.
     */
    private static boolean somebodyElses(String name, Path path) {
        return !name.endsWith("-deploy") && !name.endsWith("-revert") && holdsCheckout(path)
                && WorktreeFiles.OWN_FILES.stream().noneMatch(file -> Files.exists(path.resolve(file)));
    }

    /** A {@code -deploy} or {@code -revert} suffix is a round nobody finished, and a leftover just the same. */
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
     * A linked worktree's {@code .git} is a POINTER and outlives the registration it names, so a removal that
     * unregistered and then failed to delete leaves one behind, checkout and copied secrets and all.
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
     * Directory names a live task owns: EVERY repository's worktree, plus the deploy worktree a conflict may have
     * left.
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
                    .addKeyValue("cause", e.toString())
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
                    .addKeyValue("cause", e.toString())
                    .log();
        }
        return hits[0];
    }
}
