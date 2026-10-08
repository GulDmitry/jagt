package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The one git command a headless read may run: a subcommand and options named here, every value and path inside
 * the round's worktrees. An option not named is refused, since several read or write a file outside it.
 */
final class GitReadLine {

    private static final Set<String> DIFF_FORMAT = Set.of("-p", "--patch", "-s", "--no-patch", "-U", "--unified",
            "--stat", "--numstat", "--shortstat", "--name-only", "--name-status", "--summary", "--raw", "-w",
            "--ignore-all-space", "-b", "--ignore-space-change", "--ignore-blank-lines", "-M", "--find-renames",
            "--no-renames", "--diff-filter", "--word-diff", "--color", "--no-color", "-W", "--function-context",
            "--minimal", "--patience", "--histogram", "--relative", "--abbrev", "--full-index",
            "--output-indicator-new", "--output-indicator-old", "--output-indicator-context", "--no-ext-diff",
            "--no-textconv");
    private static final Set<String> HISTORY = Set.of("--oneline", "-n", "--max-count", "--skip", "--since",
            "--after", "--until", "--before", "--author", "--committer", "--grep", "-i", "--regexp-ignore-case",
            "--all-match", "--format", "--pretty", "--graph", "--decorate", "--no-decorate", "--merges",
            "--no-merges", "--first-parent", "--reverse", "--all", "--follow", "-S", "-G", "--abbrev-commit",
            "--date", "--left-right", "--cherry-pick", "--ancestry-path", "--topo-order", "--date-order",
            "--boundary");
    private static final Map<String, Set<String>> OPTIONS = Map.of(
            "diff", union(DIFF_FORMAT, Set.of("--cached", "--staged", "--merge-base", "--exit-code", "--quiet")),
            "log", union(DIFF_FORMAT, HISTORY),
            "show", union(DIFF_FORMAT, HISTORY),
            "status", Set.of("-s", "--short", "-b", "--branch", "--porcelain", "--long", "-u", "--untracked-files",
                    "--ignored", "-z"),
            "blame", Set.of("-L", "-w", "-M", "-C", "-e", "--show-email", "-s", "-l", "-p", "--porcelain",
                    "--line-porcelain", "--date", "-n", "--show-number", "-f", "--show-name", "--root",
                    "--no-ext-diff", "--no-textconv"),
            "merge-base", Set.of("--is-ancestor", "--fork-point", "--all", "--octopus", "--independent"),
            "rev-parse", Set.of("--abbrev-ref", "--short", "--verify", "-q", "--quiet", "--show-toplevel",
                    "--symbolic-full-name", "--show-prefix", "--is-inside-work-tree"),
            "ls-files", Set.of("-c", "--cached", "-m", "--modified", "-o", "--others", "-d", "--deleted", "-s",
                    "--stage", "-u", "--unmerged", "--exclude-standard", "-z", "--full-name", "--error-unmatch"));
    private static final Set<String> SHORT_WITH_VALUE = Set.of("-U", "-n", "-L", "-M", "-S", "-G", "-u", "-C");
    /** A repository's config may name a program for a diff or a text conversion. */
    private static final Set<String> RUNS_CONFIGURED_PROGRAMS = Set.of("diff", "log", "show", "blame");
    private static final List<String> NO_CONFIGURED_PROGRAMS = List.of("--no-ext-diff", "--no-textconv");
    private static final Pattern COUNT = Pattern.compile("-\\d+");
    /** Words a shell passes on unchanged: no quote, expansion, glob or operator, and none opening with {@code =}. */
    private static final Pattern LITERAL = Pattern.compile("[\\w./:@%+,-][\\w./=:@%+,-]*( [\\w./:@%+,-][\\w./=:@%+,-]*)*");
    /** A format naming a signature runs the configured gpg. */
    private static final String SIGNATURE = "%G";

    private GitReadLine() {
    }

    static Optional<String> refusal(ReadScope read, String command, Path here) {
        List<String> words = List.of(command.split(" "));
        if (!LITERAL.matcher(command).matches() || command.contains(SIGNATURE) || words.size() < 2
                || !"git".equals(words.getFirst())) {
            return Optional.of("jagt refuses this command in a read: one read-only git command in plain words, single"
                    + " spaces, no quote, ~, ^, brace, glob, %G or anything around it.");
        }
        int at = 1;
        Path repository = here;
        if ("-C".equals(words.get(at)) && words.size() > 3) {
            if (!ReadGate.inside(read, here, words.get(at + 1))) {
                return Optional.of("jagt refuses git outside the round's worktrees.");
            }
            repository = here.resolve(words.get(at + 1));
            at += 2;
        }
        String subcommand = words.get(at);
        Set<String> options = OPTIONS.get(subcommand);
        if (options == null) {
            return Optional.of("jagt refuses git " + subcommand + " in a read: only " + OPTIONS.keySet() + " run.");
        }
        List<String> arguments = words.subList(at + 1, words.size());
        if (RUNS_CONFIGURED_PROGRAMS.contains(subcommand) && !arguments.containsAll(NO_CONFIGURED_PROGRAMS)) {
            return Optional.of("jagt refuses git " + subcommand + " in a read without --no-ext-diff --no-textconv:"
                    + " a repository's config would run a program.");
        }
        boolean paths = false;
        for (String argument : arguments) {
            paths |= "--".equals(argument);
            String value = paths || !argument.startsWith("-") ? argument : valueOf(argument, subcommand, options);
            if (value == null) {
                return Optional.of("jagt refuses git " + subcommand + " " + argument + " in a read: only "
                        + options.stream().sorted().collect(Collectors.joining(" ")) + " are allowed.");
            }
            if (!insideEveryPathOf(read, repository, value)) {
                return Optional.of("jagt refuses the path " + argument + ": a read stays inside the round's worktrees.");
            }
        }
        return Optional.empty();
    }

    /** What an allowed option carries, empty when it carries nothing, or null when the option is not allowed. */
    private static String valueOf(String argument, String subcommand, Set<String> options) {
        String name = argument.split("=", 2)[0];
        if (options.contains(name)) {
            return argument.length() > name.length() ? argument.substring(name.length() + 1) : "";
        }
        if (COUNT.matcher(argument).matches() && options.containsAll(HISTORY)) {
            return "";
        }
        String flag = argument.length() > 2 && !argument.startsWith("--") ? argument.substring(0, 2) : null;
        return flag != null && options.contains(flag) && SHORT_WITH_VALUE.contains(flag) ? argument.substring(2) : null;
    }

    private static boolean insideEveryPathOf(ReadScope read, Path repository, String value) {
        return Stream.of(value.split("[=:,]")).filter(piece -> piece.startsWith("/") || piece.startsWith("~")
                || ReadGate.PARENT.matcher(piece).find()).allMatch(piece -> ReadGate.inside(read, repository, piece));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
    }
}
