package dev.jagt.orchestrator.service.worktree;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.host.Processes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the data folder's tree and moves into it what ONE older folder kept, the one holding
 * {@code state.json}, never over a file there. Worktrees and scratch folders stay where the tasks
 * recorded them; a moved clone has them repaired.
 */
@Component
@Slf4j
public class DataFolderStart {

  private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30);

  private final OrchestratorPaths paths;
  private final Processes processes;
  private final String previousData;

  public DataFolderStart(
      OrchestratorPaths paths,
      Processes processes,
      @Value("${orchestrator.previous-data:}") String previousData) {
    this.paths = paths;
    this.processes = processes;
    this.previousData = previousData;
  }

  @EventListener(ContextRefreshedEvent.class)
  public void prepare() {
    try {
      Files.createDirectories(paths.data());
      olderFolder().ifPresent(this::bringIn);
      for (Path folder : paths.folders()) {
        Files.createDirectories(folder);
      }
    } catch (IOException e) {
      log.atWarn()
          .setMessage("data folder not created")
          .addKeyValue("path", paths.data())
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the start check names it")
          .log();
    }
  }

  private Optional<Path> olderFolder() {
    if (Files.exists(paths.stateFile())) {
      return Optional.empty();
    }
    List<Path> folders = new ArrayList<>(List.of(paths.data()));
    if (!previousData.isBlank()) {
      folders.add(Path.of(previousData).toAbsolutePath().normalize());
    }
    folders.add(paths.root());
    return folders.stream()
        .distinct()
        .filter(older -> Files.exists(older.resolve("state.json")))
        .findFirst();
  }

  private void bringIn(Path older) {
    paths.placed().forEach((name, to) -> place(older.resolve(name), to));
    paths
        .placedEntries()
        .forEach((folder, target) ->
            listed(older.resolve(folder))
                .forEach(entry -> place(entry, target.apply(entry.getFileName().toString()))));
    for (Path file : listed(older)) {
      Path to = paths.placedVerdicts(file.getFileName().toString());
      if (to != null) {
        place(file, to);
      }
    }
    dropStaleToken(older);
  }

  private void dropStaleToken(Path older) {
    Path token = older.resolve(paths.masterToken().getFileName());
    if (token.equals(paths.masterToken()) || !Files.isRegularFile(token)) {
      return;
    }
    try {
      Files.delete(token);
      log.atInfo()
          .setMessage("older master token deleted")
          .addKeyValue("path", token)
          .addKeyValue("effect", "each start draws a new one")
          .log();
    } catch (IOException e) {
      log.atWarn()
          .setMessage("older master token not deleted")
          .addKeyValue("path", token)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "a stale credential stays in the older folder")
          .log();
    }
  }

  private void place(Path from, Path to) {
    if (!Files.exists(from, LinkOption.NOFOLLOW_LINKS) || from.equals(to)) {
      return;
    }
    if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
      log.atWarn()
          .setMessage("older file kept, the data folder holds one")
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .addKeyValue("effect", "the older file stays where it is")
          .log();
      return;
    }
    move(from, to);
  }

  private static List<Path> listed(Path folder) {
    try (Stream<Path> entries = Files.list(folder)) {
      return entries.toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private void move(Path from, Path to) {
    try {
      Files.createDirectories(to.getParent());
      Files.move(from, to);
      log.atInfo()
          .setMessage("older file moved into the data folder")
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .log();
      if (Files.isDirectory(to.resolve(".git"))) {
        repairWorktrees(to);
      }
    } catch (IOException e) {
      log.atWarn()
          .setMessage("older file not moved")
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the older file stays and jagt starts without it")
          .log();
    }
  }

  private void repairWorktrees(Path clone) {
    List<String> command = new ArrayList<>(List.of("git", "worktree", "repair"));
    processes
        .run(clone, GIT_TIMEOUT, List.of("git", "worktree", "list", "--porcelain"))
        .stdout()
        .lines()
        .filter(line -> line.startsWith("worktree "))
        .map(line -> Path.of(line.substring("worktree ".length())))
        .filter(worktree -> !worktree.equals(clone) && Files.isDirectory(worktree))
        .forEach(worktree -> command.add(worktree.toString()));
    Processes.Result repair = processes.run(clone, GIT_TIMEOUT, command);
    if (repair.exitCode() != 0) {
      log.atWarn()
          .setMessage("worktrees of a moved clone not repaired")
          .addKeyValue("path", clone)
          .addKeyValue("cause", repair.stderr())
          .addKeyValue("effect", "a task's worktree there cannot reach its repository")
          .log();
    }
  }
}
