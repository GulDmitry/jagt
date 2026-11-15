package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.service.worktree.WorktreeNoise;
import dev.jagt.orchestrator.task.launch.TaskName;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

/**
 * The worktree directories no task owns: found, and deleted one at a time by name. An orphan can
 * hold uncommitted work AND copies of gitignored secrets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorktreeOrphans {

  public record Orphan(Path path, String projectKey, int secretFiles) {}

  private final ConfigService configService;
  private final StateService stateService;
  private final GitCommands git;
  private final OrchestratorPaths paths;

  /** Deletes the one orphan directory called {@code name}; any other path is refused. */
  public String delete(String name) {
    Orphan orphan =
        scan().stream()
            .filter(found -> found.path().getFileName().toString().equals(name))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "no orphaned worktree called " + name + " — `worktrees` lists them"));
    try {
      ProjectConfig project = configService.load().projects().get(orphan.projectKey());
      Path repository = project == null ? null : Path.of(project.path());
      if (repository != null && registeredIn(repository, orphan.path())) {
        return removeRegistered(repository, orphan.path());
      }
      FileSystemUtils.deleteRecursively(orphan.path());
    } catch (IOException e) {
      log.atWarn()
          .setMessage("worktree delete failed")
          .addKeyValue("path", orphan.path())
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the directory stays")
          .log();
      return "could not delete " + orphan.path() + ": " + e;
    }
    log.atInfo().setMessage("worktree deleted").addKeyValue("path", orphan.path()).log();
    return "deleted " + orphan.path();
  }

  private boolean registeredIn(Path repository, Path directory) {
    Processes.Result listing =
        git.run(repository, List.of("git", "worktree", "list", "--porcelain"));
    return listing.exitCode() == 0
        && listing
            .stdout()
            .lines()
            .filter(line -> line.startsWith("worktree "))
            .map(line -> Path.of(line.substring("worktree ".length())))
            .anyMatch(listed -> GitCommands.sameDirectory(listed, directory));
  }

  private String removeRegistered(Path repository, Path directory) {
    Processes.Result removal =
        git.run(repository, List.of("git", "worktree", "remove", "--force", directory.toString()));
    if (removal.exitCode() != 0) {
      log.atWarn()
          .setMessage("worktree remove failed")
          .addKeyValue("path", directory)
          .addKeyValue("cause", removal.stderr())
          .addKeyValue("effect", "the worktree stays")
          .log();
      return "could not remove " + directory + ": " + removal.stderr().strip();
    }
    log.atInfo().setMessage("worktree removed").addKeyValue("path", directory).log();
    return "deleted " + directory;
  }

  public List<Orphan> scan() {
    Map<Path, Orphan> found = new LinkedHashMap<>();
    Set<String> owned = ownedDirectoryNames();
    ConfigService.ConfigFile config = configService.load();
    List<PathMatcher> secretMatchers =
        WorktreeFiles.localFileMatchers(config.worktree().copyGlobsOrDefault());
    config
        .projects()
        .forEach((projectKey, project) -> {
          Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
          besideRepository(projectKey, projectPath, owned, secretMatchers, found);
          underData(projectKey, owned, secretMatchers, found);
        });
    return List.copyOf(found.values());
  }

  private void besideRepository(
      String projectKey,
      Path projectPath,
      Set<String> owned,
      List<PathMatcher> secretMatchers,
      Map<Path, Orphan> found) {
    Path parent = projectPath.getParent();
    if (parent == null || !Files.isDirectory(parent)) {
      return;
    }
    for (String name :
        orphanNames(
            directoryNames(parent),
            projectKey,
            owned,
            name -> holdsCheckout(parent.resolve(name)))) {
      Path path = parent.resolve(name);
      if (!path.equals(projectPath) && !somebodyElses(name, path)) {
        found.putIfAbsent(
            path, new Orphan(path, projectKey, countSecretFiles(path, secretMatchers)));
      }
    }
  }

  private void underData(
      String projectKey,
      Set<String> owned,
      List<PathMatcher> secretMatchers,
      Map<Path, Orphan> found) {
    Path own = paths.worktrees().resolve(projectKey);
    if (!Files.isDirectory(own)) {
      return;
    }
    directoryNames(own).stream()
        .filter(name -> !owned.contains(name))
        .map(own::resolve)
        .forEach(path ->
            found.putIfAbsent(
                path, new Orphan(path, projectKey, countSecretFiles(path, secretMatchers))));
  }

  /**
   * A checkout under a name jagt could have cut but carrying no file of jagt's: a worktree somebody
   * made by hand, whose work is nobody's business here. Only the task's own name is ambiguous — a
   * {@code -deploy} or {@code -revert} one nothing but jagt ever writes.
   */
  private static boolean somebodyElses(String name, Path path) {
    return !name.endsWith("-deploy")
        && !name.endsWith("-revert")
        && holdsCheckout(path)
        && WorktreeFiles.OWN_FILES.stream().noneMatch(file -> Files.exists(path.resolve(file)));
  }

  /**
   * A {@code -deploy} or {@code -revert} suffix is a round nobody finished, and a leftover just the
   * same.
   */
  static Set<String> orphanNames(
      List<String> directoryNames,
      String projectKey,
      Set<String> ownedNames,
      java.util.function.Predicate<String> holdsCheckout) {
    return directoryNames.stream()
        .filter(name ->
            name.endsWith("-" + projectKey) || name.endsWith("-deploy") || name.endsWith("-revert"))
        .filter(name ->
            !ownedNames.contains(name) || (name.endsWith("-deploy") && !holdsCheckout.test(name)))
        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
  }

  /**
   * A linked worktree's {@code .git} is a POINTER and outlives the registration it names, so a
   * removal that unregistered and then failed to delete leaves one behind, checkout and copied
   * secrets and all.
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
   * Directory names a live task owns: EVERY repository's worktree, plus the deploy and revert
   * worktrees a conflict may have left.
   */
  private Set<String> ownedDirectoryNames() {
    Set<String> owned = new java.util.HashSet<>();
    stateService
        .tasks()
        .forEach((taskId, task) -> {
          owned.add(TaskName.slug(taskId) + "-deploy");
          owned.add(TaskName.slug(taskId) + "-revert");
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
      log.atWarn()
          .setMessage("worktree scan failed")
          .addKeyValue("path", parent)
          .addKeyValue("cause", e.toString())
          .log();
      return List.of();
    }
  }

  private int countSecretFiles(Path worktree, List<PathMatcher> matchers) {
    if (matchers.isEmpty()) {
      return 0;
    }
    int[] hits = {0};
    try {
      Files.walkFileTree(
          worktree,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              String name = dir.getFileName().toString();
              return WorktreeNoise.UNWALKED.contains(name) || WorktreeNoise.isResidue(name)
                  ? FileVisitResult.SKIP_SUBTREE
                  : FileVisitResult.CONTINUE;
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
      log.atWarn()
          .setMessage("secret scan failed")
          .addKeyValue("path", worktree)
          .addKeyValue("cause", e.toString())
          .log();
    }
    return hits[0];
  }
}
