package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.Probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The acceptance probes a plan declares, in a fenced {@code probes} block, one per line:
 * {@code command [exit=<n>] <argv…>}, {@code ref <ref>} or {@code artifact <path>}.
 */
public final class PlanProbes {

    public static final int MAX = 10;

    private static final String OPEN = "```probes";
    private static final String CLOSE = "```";
    private static final String FIELD = "plan.md probes";
    /** A command runs as argv with no shell, so none of these could mean what it says. */
    private static final Pattern SHELL = Pattern.compile("[|;&<>$`\\\\]");
    private static final Pattern EXIT = Pattern.compile("exit=(\\d{1,3})");
    private static final Pattern WORD = Pattern.compile("'([^']*)'|\"([^\"]*)\"|(\\S+)");

    public record Read(List<Probe> probes, List<Violation> violations) {
    }

    private PlanProbes() {
    }

    public static Read of(String plan) {
        List<Probe> probes = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        boolean inBlock = false;
        for (String line : plan.lines().map(String::strip).toList()) {
            if (!inBlock) {
                inBlock = line.equals(OPEN);
            } else if (line.equals(CLOSE)) {
                inBlock = false;
            } else if (!line.isEmpty()) {
                probe(line).ifPresentOrElse(probes::add, () -> violations.add(new Violation(FIELD, "`" + line
                        + "` is none of `command [exit=<n>] <argv…>`, `ref <ref>`, `artifact <relative path>`;"
                        + " a command runs with no shell, so no | ; & < > $ ` or \\")));
            }
        }
        if (inBlock) {
            violations.add(new Violation(FIELD, "close the ```probes block with ```"));
        }
        if (probes.size() > MAX) {
            violations.add(new Violation(FIELD, "at most " + MAX + " probes, not " + probes.size()));
        }
        return new Read(List.copyOf(probes), List.copyOf(violations));
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
            case "ref" -> words.size() == 2 && !rest.startsWith("-")
                    ? Optional.of(Probe.specified(Probe.Kind.REF, List.of(rest), 0)) : Optional.empty();
            case "artifact" -> words.size() == 2 && inside(rest)
                    ? Optional.of(Probe.specified(Probe.Kind.ARTIFACT, List.of(rest), 0)) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static Optional<Probe> command(List<String> words) {
        Matcher exit = EXIT.matcher(words.getFirst());
        if (!exit.matches()) {
            return Optional.of(Probe.specified(Probe.Kind.COMMAND, words, 0));
        }
        int code = Integer.parseInt(exit.group(1));
        return code <= 255 && words.size() > 1
                ? Optional.of(Probe.specified(Probe.Kind.COMMAND, words.subList(1, words.size()), code))
                : Optional.empty();
    }

    /** Quotes group words and nothing else; an unclosed one is no line. */
    private static Optional<List<String>> words(String line) {
        if (line.chars().filter(c -> c == '\'').count() % 2 != 0 || line.chars().filter(c -> c == '"').count() % 2 != 0) {
            return Optional.empty();
        }
        List<String> words = new ArrayList<>();
        Matcher word = WORD.matcher(line);
        while (word.find()) {
            words.add(word.group(1) != null ? word.group(1) : word.group(2) != null ? word.group(2) : word.group(3));
        }
        return Optional.of(words);
    }

    private static boolean inside(String path) {
        return !path.startsWith("/") && !path.startsWith("~")
                && List.of(path.split("/")).stream().noneMatch(".."::equals);
    }
}
