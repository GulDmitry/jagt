package dev.jagt.orchestrator.surface.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The calls a session may be refused: a line reaching the board or the Master's token, a write to the code host
 * past the push, {@code $'…'} or {@code $"…"} on a git line, a push that could switch off the hook, a push naming
 * no branch or one not the task's own, a delete of the branch its review request is built on, and a force without
 * the lease. Detaching a worktree's upstream removes the DEFAULT target and nothing else, so an explicit
 * {@code git push origin dev} still needs refusing. This is a gate on a few lines, not a permission layer.
 * What is read is the command LINE, so a push assembled at runtime is not seen.
 */
public final class ToolGate {

    /** Only a shell command can push; every other tool is answered with nothing. */
    private static final String SHELL_TOOL = "Bash";
    /** An {@code &} beside a redirection ({@code 2>&1}, {@code &>}) separates nothing. */
    private static final String SEPARATORS = "&&|\\|\\||;|\\n|\\||(?<![<>])&(?!>)|\\(|\\)|`|\\$\\(";
    private static final List<String> GIT_OPTION_WITH_VALUE =
            List.of("-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--config-env");
    private static final List<String> PUSH_OPTION_WITH_VALUE =
            List.of("-o", "--push-option", "--repo", "--receive-pack", "--exec");
    /** git takes any prefix of a long option, and {@code d} inside a bundle of short ones. */
    private static final Pattern DELETE = Pattern.compile("--d(e(l(e(te?)?)?)?)?|-[a-zA-Z]*d[a-zA-Z]*");
    /** What the branch a worktree is on is called, so a push of it is a push of the task's branch. */
    private static final String CURRENT_BRANCH = "HEAD";
    /** The board acts as the human, and the token as the Master: neither is a session's to reach. */
    private static final String BOARD = "(?i):0*%d\\b|master-token|mcp_client\\.js";
    private static final String QUOTING = "[\\\\'\"]";
    private static final Pattern PUSH = Pattern.compile("\\bpush\\b");
    /** The shell decodes escapes inside these, which the line is not read through. */
    private static final Pattern UNREAD_QUOTING = Pattern.compile("\\$['\"]");
    private static final Pattern GIT_WORD = Pattern.compile("\\bgit\\b");
    /** git's plumbing writes a remote ref with no pre-push hook. */
    private static final Pattern PLUMBING = Pattern.compile("(send|receive)-pack|http-push");
    private static final Pattern GIT = Pattern.compile("(\\S*/)?git");
    private static final Pattern FORCE = Pattern.compile("--force|-[a-zA-Z]*f[a-zA-Z]*");
    private static final Pattern HOOK_OFF = Pattern.compile("--no-verify|GIT_CONFIG|(?i:core\\.hookspath)"
            + "|alias\\.|--config-env|\\benv\\s+(-\\w*[iu]\\b|-(\\s|$)|--ignore-environment|--unset)"
            + "|\\b(sh|bash|zsh|dash|ksh)\\s+-\\w*c\\b|\\beval\\b");
    /** Where the line may leave the task's branch or worktree, HEAD is no longer known to be the task's branch. */
    private static final Pattern HEAD_MOVES = Pattern.compile(
            "\\b(cd|pushd|checkout|switch|GIT_DIR|GIT_WORK_TREE)\\b|(^|\\s)(-C|--git-dir|--work-tree)(\\s|=)");

    private ToolGate() {
    }

    /** Why the call is refused, or empty when it is allowed. */
    public static Optional<String> refusal(String toolName, String line, String taskBranch, int boardPort) {
        if (!SHELL_TOOL.equalsIgnoreCase(toolName) || line == null || taskBranch == null
                || taskBranch.isBlank()) {
            return Optional.empty();
        }
        String command = line.replace("\\\n", " ").replace("\\", "").replace("''", "").replace("\"\"", "");
        if (Pattern.compile(BOARD.formatted(boardPort)).matcher(line.replaceAll(QUOTING, "")).find()) {
            return Optional.of("jagt refuses a line reaching its board or the Master's token: a session acts"
                    + " through its own MCP tools.");
        }
        if (PLUMBING.matcher(line.replaceAll(QUOTING, "")).find()
                || Stream.of(command.split(SEPARATORS)).anyMatch(HostCliLine::writes)) {
            return Optional.of("jagt refuses writing to the code host from here: gh and glab only read ("
                    + HostCliLine.readsAllowed() + "). Push " + taskBranch
                    + " with a plain `git push origin " + taskBranch + "`.");
        }
        if (UNREAD_QUOTING.matcher(line).find() && GIT_WORD.matcher(line.replaceAll(QUOTING, "")).find()) {
            return Optional.of("jagt refuses $'…' and $\"…\" on a git line: write it in plain quotes.");
        }
        if (PUSH.matcher(command).find() && (HOOK_OFF.matcher(command).find()
                || Stream.of(command.split(SEPARATORS)).anyMatch(ToolGate::configuresAPush))) {
            return Optional.of("jagt refuses a push that could skip its pre-push check: push " + taskBranch
                    + " with a plain `git push origin " + taskBranch + "`.");
        }
        boolean headIsTheTask = !HEAD_MOVES.matcher(command).find();
        String escapesQuoted = line.replace("\\\n", " ").replace("''", "").replace("\"\"", "")
                .replaceAll("\\\\(.)", "\"$1\"");
        for (String segment : escapesQuoted.split(SEPARATORS)) {
            Optional<String> refusal = pushIn(segment).flatMap(push -> refuse(push, taskBranch, headIsTheTask));
            if (refusal.isPresent()) {
                return refusal;
            }
        }
        return Optional.empty();
    }

    /**
     * The words after {@code git push} in one command, or nothing where that command is not a push. git's own
     * options sit BEFORE the subcommand ({@code git -C <dir> push …}).
     */
    private static Optional<List<String>> pushIn(String segment) {
        List<String> words = List.of(segment.trim().split("\\s+"));
        for (int at = 0; at < words.size(); at++) {
            if (!GIT.matcher(bare(words.get(at))).matches()) {
                continue;
            }
            int subcommand = afterGitOptions(words, at + 1);
            if (subcommand < words.size() && "push".equals(bare(words.get(subcommand)))) {
                return Optional.of(words.subList(subcommand + 1, words.size()));
            }
        }
        return Optional.empty();
    }

    /** A config before the subcommand may rewrite where a push lands. */
    private static boolean configuresAPush(String segment) {
        List<String> words = Stream.of(segment.trim().split("\\s+")).map(ToolGate::bare).toList();
        for (int at = 0; at < words.size(); at++) {
            int subcommand = afterGitOptions(words, at + 1);
            if (GIT.matcher(words.get(at)).matches() && subcommand < words.size()
                    && "push".equals(words.get(subcommand)) && words.subList(at + 1, subcommand).contains("-c")) {
                return true;
            }
        }
        return false;
    }

    private static int afterGitOptions(List<String> words, int from) {
        int at = from;
        while (at < words.size() && bare(words.get(at)).startsWith("-")) {
            at += GIT_OPTION_WITH_VALUE.contains(bare(words.get(at))) ? 2 : 1;
        }
        return at;
    }

    /** The shell reads {@code \\git}, {@code g'i't} and {@code "push"} as the bare word; an unclosed quote opens data. */
    private static String bare(String word) {
        boolean closed = word.chars().filter(c -> c == '\'').count() % 2 == 0
                && word.chars().filter(c -> c == '"').count() % 2 == 0;
        return closed ? word.replaceAll(QUOTING, "") : word;
    }

    /**
     * The DESTINATION decides: {@code HEAD:dev} and {@code refs/heads/x:refs/heads/dev} both write dev. A push
     * naming no ref at all is refused too, depending as it would on a config jagt did not write.
     */
    private static Optional<String> refuse(List<String> arguments, String taskBranch, boolean headIsTheTask) {
        List<String> words = commandWords(arguments);
        List<String> refspecs = refspecs(words);
        if (words.stream().anyMatch(word -> DELETE.matcher(word).matches())
                || refspecs.stream().anyMatch(refspec -> refspec.startsWith(":"))) {
            return Optional.of("jagt refuses deleting a branch from here: the review request of this task is"
                    + " built on it.");
        }
        if (refspecs.isEmpty()) {
            return Optional.of("jagt refuses a push that names no branch: push " + taskBranch
                    + " explicitly.");
        }
        if (words.stream().anyMatch(word -> FORCE.matcher(word).matches())
                || refspecs.stream().anyMatch(refspec -> refspec.startsWith("+"))) {
            return Optional.of("jagt refuses a forced push: push " + taskBranch + " under --force-with-lease.");
        }
        return refspecs.stream().filter(refspec -> !writes(refspec, taskBranch, headIsTheTask)).findFirst()
                .map(refspec -> "jagt refuses this push: " + destinationOf(refspec) + " is not this task's"
                        + " branch. Only " + taskBranch + " may be pushed from here — a shared branch is written"
                        + " by the human's `deploy`.");
    }

    /**
     * A push carries the shell's own words too: a comment ends the command, a redirection is dropped with its
     * target, and a QUOTED word is data, as a branch may be named {@code #123}.
     */
    private static List<String> commandWords(List<String> arguments) {
        List<String> words = new ArrayList<>();
        boolean redirectTarget = false;
        for (String raw : arguments) {
            String argument = unquoted(raw);
            boolean quoted = !argument.equals(raw);
            if (argument.isBlank() || !quoted && argument.startsWith("#")) {
                break;
            }
            if (redirectTarget) {
                redirectTarget = false;
            } else if (!quoted && (argument.contains(">") || argument.contains("<"))) {
                redirectTarget = argument.endsWith(">") || argument.endsWith("<");
            } else {
                words.add(argument);
            }
        }
        return words;
    }

    private static List<String> refspecs(List<String> words) {
        List<String> positional = new ArrayList<>();
        boolean valueExpected = false;
        for (String argument : words) {
            if (valueExpected) {
                valueExpected = false;
            } else if (argument.startsWith("-")) {
                valueExpected = PUSH_OPTION_WITH_VALUE.contains(argument);
            } else {
                positional.add(argument);
            }
        }
        // The first positional word is the remote; only what follows it can name a branch.
        return positional.size() < 2 ? List.of() : positional.subList(1, positional.size());
    }

    private static String unquoted(String argument) {
        return argument.replaceAll("^[\"']|[\"']$", "");
    }

    private static boolean writes(String refspec, String taskBranch, boolean headIsTheTask) {
        String destination = destinationOf(refspec);
        return destination.equals(taskBranch) || headIsTheTask && refspec.equals(CURRENT_BRANCH);
    }

    private static String destinationOf(String refspec) {
        String written = refspec.contains(":") ? refspec.substring(refspec.indexOf(':') + 1) : refspec;
        return written.replaceFirst("^\\+", "").replaceFirst("^refs/heads/", "");
    }
}
