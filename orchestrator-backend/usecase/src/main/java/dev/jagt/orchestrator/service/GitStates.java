package dev.jagt.orchestrator.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

/**
 * What git obeys around worktrees that can make it run a program or read a file: the shared and the
 * per-worktree config, and the hooks. One digest per entry, never a value: a remote URL may carry a
 * credential.
 */
@RequiredArgsConstructor
final class GitStates {

  static final int MAX_ENTRIES = 512;
  static final int MAX_VALUE = 2_000;

  /** The digest every record holds, or the text a human reads, with secrets hidden. */
  enum Form {
    DIGEST,
    CONTENT
  }

  private static final Pattern SECRET_KEY = Pattern.compile("(?i)pass|token|secret|credential");
  private static final Pattern URL_USER = Pattern.compile("(?<=://)[^/@\\s]*@");

  /** Keys git writes itself as branches are created and pushed, none naming a program or a file. */
  private static final Pattern TRACKING =
      Pattern.compile("branch\\..+\\.(remote|merge|rebase|pushremote|description)");

  private final GitCommands git;
  private final Form form;

  /** Throws where a worktree's git cannot be read. */
  Map<String, String> read(List<Path> worktrees) {
    Map<String, Map<String, String>> byFile = new LinkedHashMap<>();
    worktrees.forEach(worktree -> read(worktree, byFile));
    Map<String, String> entries = new TreeMap<>();
    boolean fold =
        form == Form.DIGEST && byFile.values().stream().mapToInt(Map::size).sum() > MAX_ENTRIES;
    byFile.forEach(
        (file, inFile) -> {
          if (fold) {
            entries.put(file, digest(inFile.toString().getBytes(StandardCharsets.UTF_8)));
          } else {
            entries.putAll(inFile);
          }
        });
    return entries;
  }

  private void read(Path worktree, Map<String, Map<String, String>> byFile) {
    List<String> dirs =
        git.run(
                worktree,
                List.of(
                    "git",
                    "rev-parse",
                    "--path-format=absolute",
                    "--absolute-git-dir",
                    "--git-common-dir"))
            .expectSuccess("git rev-parse in " + worktree)
            .stdout()
            .lines()
            .toList();
    Path gitDir = Path.of(dirs.getFirst());
    Path common = Path.of(dirs.getLast());
    Map<String, List<String>> shared = config(common, common.resolve("config"), byFile);
    Map<String, List<String>> own = config(common, gitDir.resolve("config.worktree"), byFile);
    List<String> hooksPath =
        own.getOrDefault("core.hookspath", shared.getOrDefault("core.hookspath", List.of()));
    hooks(
        hooksPath.isEmpty() ? common.resolve("hooks") : hooksDir(common, hooksPath.getLast()),
        byFile);
  }

  private Map<String, List<String>> config(
      Path common, Path file, Map<String, Map<String, String>> byFile) {
    if (!Files.isRegularFile(file)) {
      return Map.of();
    }
    String listed =
        git.run(common, List.of("git", "config", "--file", file.toString(), "--list", "-z"))
            .expectSuccess("git config --list of " + file)
            .stdout();
    Map<String, List<String>> values = new TreeMap<>();
    for (String entry : listed.split("\0", 0)) {
      int split = entry.indexOf('\n');
      String key = split < 0 ? entry : entry.substring(0, split);
      if (!key.isBlank()) {
        values
            .computeIfAbsent(key, k -> new ArrayList<>())
            .add(split < 0 ? "" : entry.substring(split + 1));
      }
    }
    Map<String, String> inFile = byFile.computeIfAbsent(file.toString(), f -> new TreeMap<>());
    values.forEach(
        (key, all) -> {
          if (!TRACKING.matcher(key).matches()) {
            inFile.put(key + " in " + file, ofValues(key, all));
          }
        });
    return values;
  }

  /**
   * Git resolves a relative path where it runs; the human's checkout is where every task's git
   * meets.
   */
  private static Path hooksDir(Path common, String hooksPath) {
    Path named =
        hooksPath.startsWith("~/")
            ? Path.of(System.getProperty("user.home"), hooksPath.substring(2))
            : Path.of(hooksPath);
    Path checkout = common.getParent() == null ? common : common.getParent();
    return checkout.resolve(named).normalize();
  }

  private String ofValues(String key, List<String> values) {
    if (form == Form.DIGEST) {
      return digest(String.join("\0", values).getBytes(StandardCharsets.UTF_8));
    }
    return SECRET_KEY.matcher(key).find()
        ? "(hidden)"
        : bounded(URL_USER.matcher(String.join("\n", values)).replaceAll("***@"));
  }

  private static String bounded(String text) {
    return text.length() <= MAX_VALUE ? text : text.substring(0, MAX_VALUE) + "\n[cut]";
  }

  private void hooks(Path dir, Map<String, Map<String, String>> byFile) {
    Map<String, String> inDir = byFile.computeIfAbsent(dir.toString(), d -> new TreeMap<>());
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> files = Files.list(dir)) {
      for (Path hook :
          files
              .filter(Files::isRegularFile)
              .filter(file -> !file.getFileName().toString().endsWith(".sample"))
              .toList()) {
        inDir.put(hook.toString(), ofHook(hook));
      }
    } catch (IOException unreadable) {
      throw new UncheckedIOException("hooks of " + dir + " unreadable", unreadable);
    }
  }

  private String ofHook(Path hook) throws IOException {
    String executable = Files.isExecutable(hook) ? "+x" : "";
    return form == Form.DIGEST
        ? digest(Files.readAllBytes(hook)) + executable
        : bounded(new String(Files.readAllBytes(hook), StandardCharsets.UTF_8));
  }

  private static String digest(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException absent) {
      throw new IllegalStateException(absent);
    }
  }
}
