package dev.jagt.orchestrator.service.worktree;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.git.GitCommands;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A task's worktrees read and searched, confined to each worktree and kept nowhere. */
@Service
@RequiredArgsConstructor
public class WorktreeCode {

  static final int MAX_FILES = 5_000;
  static final int MAX_BYTES = 512 * 1024;
  static final int MAX_HITS = 200;
  static final int MAX_LINE = 300;
  private static final String GIT_DIR = ".git";

  /** {@code cut} says {@code files} stops at {@link #MAX_FILES} of a longer list. */
  public record Tree(String project, List<String> files, boolean cut) {}

  /** Anything but {@code TEXT} carries no text. */
  public enum Kind {
    TEXT,
    BINARY,
    TOO_LARGE
  }

  public record Opened(String path, Kind kind, String text) {}

  public record Hit(String path, int line, String text) {}

  /** {@code cut} says {@code hits} stops at {@link #MAX_HITS} of more. */
  public record Found(List<Hit> hits, boolean cut) {}

  private final StateService stateService;
  private final GitCommands git;

  public List<Tree> tree(String taskIdOrAlias) {
    return repos(taskIdOrAlias).stream()
        .map(repo -> {
          List<String> files = files(root(repo));
          boolean cut = files.size() > MAX_FILES;
          return new Tree(
              Objects.requireNonNull(repo.project()),
              cut ? files.subList(0, MAX_FILES) : files,
              cut);
        })
        .toList();
  }

  public Opened file(String taskIdOrAlias, String project, String path) {
    Path file = confined(root(repoOf(taskIdOrAlias, project)), path);
    try {
      if (Files.size(file) > MAX_BYTES) {
        return new Opened(path, Kind.TOO_LARGE, "");
      }
      return decoded(Files.readAllBytes(file))
          .map(text -> new Opened(path, Kind.TEXT, text))
          .orElseGet(() -> new Opened(path, Kind.BINARY, ""));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + path, e);
    }
  }

  public Found search(String taskIdOrAlias, String project, String query) {
    if (query.isBlank()) {
      throw Refusal.byState("Nothing to search for.");
    }
    Path root = root(repoOf(taskIdOrAlias, project));
    String needle = query.toLowerCase(Locale.ROOT);
    List<Hit> hits = new ArrayList<>();
    for (String name : files(root)) {
      if (hits.size() > MAX_HITS) {
        break;
      }
      hits.addAll(hitsIn(root, name, needle));
    }
    boolean cut = hits.size() > MAX_HITS;
    return new Found(cut ? hits.subList(0, MAX_HITS) : hits, cut);
  }

  private List<Hit> hitsIn(Path root, String name, String needle) {
    try {
      Path file = confined(root, name);
      if (Files.size(file) > MAX_BYTES) {
        return List.of();
      }
      String[] lines = decoded(Files.readAllBytes(file)).orElse("").split("\n", -1);
      List<Hit> found = new ArrayList<>();
      for (int at = 0; at < lines.length; at++) {
        if (lines[at].toLowerCase(Locale.ROOT).contains(needle)) {
          found.add(new Hit(name, at + 1, clipped(lines[at])));
        }
      }
      return found;
    } catch (IOException | Refusal e) {
      return List.of();
    }
  }

  private static String clipped(String line) {
    return line.length() > MAX_LINE ? line.substring(0, MAX_LINE) : line;
  }

  private static Optional<String> decoded(byte[] bytes) {
    try {
      return Optional.of(
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString());
    } catch (CharacterCodingException e) {
      return Optional.empty();
    }
  }

  /** Every symlink resolved: a file whose real path leaves the worktree is refused. */
  private static Path confined(Path root, String path) {
    Path joined = root.resolve(path).normalize();
    if (!joined.startsWith(root)
        || joined.equals(root)
        || joined.startsWith(root.resolve(GIT_DIR))) {
      throw Refusal.byState("'" + path + "' is outside the worktree.");
    }
    try {
      Path real = joined.toRealPath();
      if (!real.startsWith(root) || !Files.isRegularFile(real)) {
        throw Refusal.byState("'" + path + "' is outside the worktree or not a file.");
      }
      return real;
    } catch (NoSuchFileException e) {
      throw Refusal.byState("No file '" + path + "' in the worktree.");
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot resolve " + path, e);
    }
  }

  private List<String> files(Path root) {
    String listed =
        git.run(
                root,
                List.of("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"))
            .expectSuccess("git ls-files in " + root)
            .stdout();
    return Arrays.stream(listed.split("\0"))
        .filter(name -> !name.isEmpty())
        .distinct()
        .sorted()
        .toList();
  }

  private static Path root(TaskRepo repo) {
    try {
      return Path.of(Objects.requireNonNull(repo.worktreePath())).toRealPath();
    } catch (IOException e) {
      throw Refusal.byState("The worktree of " + repo.project() + " is not on disk.");
    }
  }

  private TaskRepo repoOf(String taskIdOrAlias, String project) {
    List<TaskRepo> repos = repos(taskIdOrAlias);
    return repos.stream()
        .filter(repo -> project.isEmpty() || project.equals(repo.project()))
        .findFirst()
        .orElseThrow(() -> Refusal.byState("The task has no repository '" + project + "'."));
  }

  private List<TaskRepo> repos(String taskIdOrAlias) {
    String taskId = stateService.canonicalTaskId(taskIdOrAlias);
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    return task.unplaced() ? List.of() : task.repos();
  }
}
