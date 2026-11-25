package dev.jagt.orchestrator.adapter.worktree;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.worktree.Specs;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

@Component
public class OpenSpec implements Specs {

  private static final String BINARY = "openspec";
  private static final String HOME = "openspec";
  private static final Duration LIMIT = Duration.ofMinutes(2);

  /** An archived change is named `YYYY-MM-DD-<change>`. */
  private static final int ARCHIVE_DATE = "2026-01-01-".length();

  private static final JsonMapper MAPPER = new JsonMapper();

  private final Processes processes;
  private final String binary;

  private final Host host;

  public OpenSpec(Processes processes, Host host) {
    this.processes = processes;
    this.host = host;
    this.binary = host.resolve(BINARY);
  }

  @Override
  public Optional<String> owed(Path worktree, @Nullable String root, String taskId) {
    Home home = Home.of(worktree, root);
    if (!home.keepsSpecs()) {
      return Optional.empty();
    }
    if (home.misnamed()) {
      return Optional.of(home.misnamedRefusal());
    }
    String change = changeName(taskId);
    try {
      List<String> open = open(home, change);
      if (open.isEmpty()) {
        return archived(home, change) ? Optional.empty() : Optional.of(howToOpen(home, change));
      }
      String refused =
          open.stream()
              .map(name -> refusal(home, name))
              .flatMap(Optional::stream)
              .collect(Collectors.joining("\n"));
      return refused.isEmpty() ? Optional.empty() : Optional.of(refused);
    } catch (UncheckedIOException e) {
      return Optional.of(
          "could not read "
              + home.shown()
              + "/changes: "
              + Objects.requireNonNull(e.getCause()).getMessage());
    }
  }

  @Override
  public void fold(Path worktree, @Nullable String root, String taskId) {
    Home home = Home.of(worktree, root);
    if (!home.keepsSpecs()) {
      return;
    }
    if (home.misnamed()) {
      throw new IllegalStateException(home.misnamedRefusal());
    }
    for (String name : open(home, changeName(taskId))) {
      refusal(home, name)
          .ifPresent(refused -> {
            throw new IllegalStateException(refused);
          });
      run(home, List.of("archive", name, "-y")).expectSuccess("openspec archive " + name);
    }
  }

  static String changeName(String taskId) {
    return taskId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  /** The CLI reads the specs only from an `openspec` folder of the directory it runs in. */
  private record Home(Path folder, String shown) {

    static Home of(Path worktree, @Nullable String root) {
      String shown = root == null || root.isBlank() ? HOME : Path.of(root).normalize().toString();
      return new Home(worktree.resolve(shown), shown);
    }

    boolean keepsSpecs() {
      return Files.isDirectory(folder);
    }

    boolean misnamed() {
      return !Objects.requireNonNull(folder.getFileName()).toString().equals(HOME);
    }

    String misnamedRefusal() {
      return shown + " is not a folder named " + HOME + ", the only one the openspec CLI reads";
    }

    Path directory() {
      return Objects.requireNonNull(folder.getParent());
    }

    Path changes() {
      return folder.resolve("changes");
    }
  }

  /**
   * `archive -y` folds a change it never validated; a delta it would refuse is an INFO issue at
   * exit 0, so any issue refuses.
   */
  private Optional<String> refusal(Home home, String name) {
    Processes.Result result =
        run(
            home,
            List.of(
                "validate", name, "--type", "change", "--strict", "--no-interactive", "--json"));
    JsonNode item;
    try {
      item = MAPPER.readTree(result.stdout()).path("items").path(0);
    } catch (JacksonException e) {
      item = MissingNode.getInstance();
    }
    if (item.isMissingNode()) {
      return Optional.of(refusalHeader(home, name) + raw(result));
    }
    List<String> issues = new ArrayList<>();
    for (JsonNode issue : item.path("issues")) {
      issues.add(
          issue.path("level").asString()
              + " "
              + issue.path("path").asString()
              + ": "
              + issue.path("message").asString());
    }
    if (issues.isEmpty() && result.exitCode() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        refusalHeader(home, name) + (issues.isEmpty() ? raw(result) : String.join("\n", issues)));
  }

  private static String raw(Processes.Result result) {
    return (result.stdout() + "\n" + result.stderr()).strip();
  }

  private static String refusalHeader(Home home, String name) {
    return home.shown()
        + "/changes/"
        + name
        + " does not validate — fix every line and hand back again:\n";
  }

  private Processes.Result run(Home home, List<String> arguments) {
    if (Executables.unresolved(binary)) {
      throw new IllegalStateException(
          "this repository keeps openspec/ and jagt finds no `openspec` CLI: install it (npm i -g"
              + " @fission-ai/openspec) — ask the human, this is not yours to fix");
    }
    return processes.run(
        home.directory(),
        LIMIT,
        environment(),
        Stream.concat(Stream.of(binary), arguments.stream()).toList());
  }

  /**
   * The CLI is a `#!/usr/bin/env node` script, and an install puts node beside it rather than on
   * jagt's PATH.
   */
  private Map<String, String> environment() {
    String inherited = host.env("PATH").orElse(null);
    String path =
        Path.of(binary).getParent() + (inherited == null ? "" : File.pathSeparator + inherited);
    return Map.of("OPENSPEC_TELEMETRY", "0", "PATH", path);
  }

  private static List<String> open(Home home, String change) {
    return names(home.changes(), name -> !name.equals("archive") && isOf(name, change));
  }

  private static boolean archived(Home home, String change) {
    return !names(
            home.changes().resolve("archive"),
            name -> name.length() > ARCHIVE_DATE && isOf(name.substring(ARCHIVE_DATE), change))
        .isEmpty();
  }

  /**
   * A later round opens `<change>-2`, since the first is archived under its own name; a change may
   * carry a slug and the key in any case.
   */
  private static boolean isOf(String name, String change) {
    String lower = name.toLowerCase(Locale.ROOT);
    return lower.equals(change) || lower.startsWith(change + "-");
  }

  private static List<String> names(Path directory, Predicate<String> wanted) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(directory)) {
      return entries
          .filter(Files::isDirectory)
          .map(entry -> entry.getFileName().toString())
          .filter(wanted)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String howToOpen(Home home, String change) {
    return "this repository records its behaviour in "
        + home.shown()
        + "/specs, and your hand-back carries no change: in "
        + home.directory()
        + " run `openspec new change "
        + change
        + "`, read `openspec instructions specs --change "
        + change
        + "` for the delta format and rules, then write its delta under "
        + home.shown()
        + "/changes/"
        + change
        + "/specs/, or set"
        + " `skip_specs: true` in its .openspec.yaml when no behaviour changes";
  }
}
