package dev.jagt.orchestrator.protocol.plan;

import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.task.plan.Probe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The acceptance probes a plan declares, in a fenced {@code probes} block, one per line: {@code
 * command [repo=<project>] [exit=<n>] <argv…>}, {@code ref <ref>} or {@code artifact <path>}.
 */
public final class PlanProbes {

  public static final int MAX = 10;

  private static final String OPEN = "```probes";
  private static final String CLOSE = "```";
  private static final String FIELD = "plan.md probes";

  /** A command runs as argv with no shell, so none of these could mean what it says. */
  private static final Pattern SHELL = Pattern.compile("[|;&<>$`\\\\]");

  private static final Pattern OPTION = Pattern.compile("(repo|exit)=(.*)");
  private static final Pattern EXIT = Pattern.compile("\\d{1,3}");
  private static final Pattern REPO = Pattern.compile("[^/\\s]+");
  private static final Pattern WORD = Pattern.compile("'([^']*)'|\"([^\"]*)\"|(\\S+)");

  public record Read(List<Probe> probes, List<Violation> violations) {}

  private PlanProbes() {}

  public static Read of(String plan) {
    List<Probe> probes = new ArrayList<>();
    List<Violation> violations = new ArrayList<>();
    PlanSection block = PlanSection.fenced(OPEN, CLOSE, plan);
    for (String line : block.lines()) {
      if (!line.isEmpty()) {
        probe(line)
            .ifPresentOrElse(
                probes::add,
                () ->
                    violations.add(
                        new Violation(
                            FIELD,
                            "`"
                                + line
                                + "` is none of `command [repo=<project>] [exit=<n>] <argv…>`, `ref"
                                + " <ref>`, `artifact <relative path>`; a command runs with no"
                                + " shell, so no | ; & < > $ ` or \\")));
      }
    }
    if (!block.closed()) {
      violations.add(new Violation(FIELD, "close the ```probes block with ```"));
    }
    if (probes.size() > MAX) {
      violations.add(new Violation(FIELD, "at most " + MAX + " probes, not " + probes.size()));
    }
    violations.addAll(execWithoutUp(probes));
    return new Read(List.copyOf(probes), List.copyOf(violations));
  }

  private static List<Violation> execWithoutUp(List<Probe> probes) {
    List<Violation> violations = new ArrayList<>();
    for (int at = 0; at < probes.size(); at++) {
      Probe probe = probes.get(at);
      boolean started =
          probes.subList(0, at).stream()
              .anyMatch(earlier ->
                  Objects.equals(earlier.repo(), probe.repo()) && composes(earlier, "up"));
      if (composes(probe, "exec") && !started) {
        violations.add(
            new Violation(
                FIELD,
                "`"
                    + probe
                    + "` execs into a compose service no earlier probe started; probes run one by"
                    + " one with no shell, so list `command"
                    + (probe.repo() == null ? "" : " repo=" + probe.repo())
                    + " docker compose up -d --wait` before it"));
      }
    }
    return violations;
  }

  private static boolean composes(Probe probe, String subcommand) {
    if (probe.kind() != Probe.Kind.COMMAND) {
      return false;
    }
    List<String> words = probe.words();
    int compose = Math.max(words.indexOf("compose"), words.indexOf("docker-compose"));
    return compose >= 0 && words.subList(compose + 1, words.size()).contains(subcommand);
  }

  private static Optional<Probe> probe(String line) {
    if (SHELL.matcher(line).find()) {
      return Optional.empty();
    }
    Optional<List<String>> split = words(line);
    if (split.isEmpty() || split.get().size() < 2) {
      return Optional.empty();
    }
    List<String> words = split.get();
    String rest = words.get(1);
    return switch (words.getFirst().toLowerCase(Locale.ROOT)) {
      case "command" -> command(words.subList(1, words.size()));
      case "ref" ->
          words.size() == 2 && !rest.startsWith("-")
              ? Optional.of(Probe.specified(Probe.Kind.REF, List.of(rest), 0))
              : Optional.empty();
      case "artifact" ->
          words.size() == 2 && inside(rest)
              ? Optional.of(Probe.specified(Probe.Kind.ARTIFACT, List.of(rest), 0))
              : Optional.empty();
      default -> Optional.empty();
    };
  }

  private static Optional<Probe> command(List<String> words) {
    Map<String, String> options = new HashMap<>();
    Matcher option = OPTION.matcher("");
    int at = 0;
    int given = 0;
    while (at < words.size() && option.reset(words.get(at)).matches()) {
      options.put(option.group(1), option.group(2));
      given++;
      at++;
    }
    String repo = options.get("repo");
    String exit = options.getOrDefault("exit", "0");
    boolean valid =
        at < words.size()
            && given == options.size()
            && EXIT.matcher(exit).matches()
            && Integer.parseInt(exit) <= 255
            && (repo == null || REPO.matcher(repo).matches());
    return valid
        ? Optional.of(
            Probe.specified(
                Probe.Kind.COMMAND, repo, words.subList(at, words.size()), Integer.parseInt(exit)))
        : Optional.empty();
  }

  /** Quotes group words and nothing else; an unclosed one is no line. */
  private static Optional<List<String>> words(String line) {
    if (line.chars().filter(c -> c == '\'').count() % 2 != 0
        || line.chars().filter(c -> c == '"').count() % 2 != 0) {
      return Optional.empty();
    }
    List<String> words = new ArrayList<>();
    Matcher word = WORD.matcher(line);
    while (word.find()) {
      words.add(
          word.group(1) != null
              ? word.group(1)
              : word.group(2) != null ? word.group(2) : word.group(3));
    }
    return Optional.of(words);
  }

  private static boolean inside(String path) {
    return !path.startsWith("/")
        && !path.startsWith("~")
        && List.of(path.split("/")).stream().noneMatch(".."::equals);
  }
}
