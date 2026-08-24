package dev.jagt.orchestrator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one call a session may be refused: a push whose destination is not the task's own branch, and a delete of
 * the branch its review request is built on.
 *
 * <p>A worktree's upstream is detached, which removes the DEFAULT target and nothing else — an explicit
 * {@code git push origin dev} was still the agent's to run, and a shared branch is written by {@code deploy}
 * alone. Everything that is not a push is allowed without a word: this is a gate on one command, not a
 * permission layer.
 *
 * <p>What is read is the command LINE, so a push assembled at runtime (a variable, a script the agent wrote) is
 * not seen. That is the honest limit of reading a string, and the reason the prompt rules stay.
 */
public final class ToolGate {

    /** Only a shell command can push; every other tool is answered with nothing. */
    private static final String SHELL_TOOL = "Bash";
    /** Where one command ends and the next begins, so a refusal reads only the push's own words. */
    private static final String SEPARATORS = "&&|\\|\\||;|\\n|\\||\\(|\\)|`|\\$\\(";
    /** git's own options, before the subcommand: these take the next word with them. */
    private static final List<String> GIT_OPTION_WITH_VALUE =
            List.of("-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--config-env");
    /** Push options whose VALUE is the next word, which is therefore not a refspec. */
    private static final List<String> PUSH_OPTION_WITH_VALUE =
            List.of("-o", "--push-option", "--repo", "--receive-pack", "--exec");
    /** Asking for a branch to be REMOVED, whichever way it is spelled. */
    private static final List<String> DELETES = List.of("--delete", "-d");
    /** What the branch a worktree is on is called, so a push of it is a push of the task's branch. */
    private static final String CURRENT_BRANCH = "HEAD";

    private ToolGate() {
    }

    /**
     * @return why the call is refused, or empty when it is allowed
     */
    public static Optional<String> refusal(String toolName, String command, String taskBranch) {
        if (!SHELL_TOOL.equalsIgnoreCase(toolName) || command == null || taskBranch == null
                || taskBranch.isBlank()) {
            return Optional.empty();
        }
        for (String segment : command.split(SEPARATORS)) {
            Optional<String> refusal = pushIn(segment).flatMap(push -> refuse(push, taskBranch));
            if (refusal.isPresent()) {
                return refusal;
            }
        }
        return Optional.empty();
    }

    /**
     * The words after {@code git push} in one command, or nothing where that command is not a push. git's own
     * options sit BEFORE the subcommand ({@code git -C <dir> push …}), and a gate that read only the word after
     * {@code git} was bypassed by every one of them.
     */
    private static Optional<List<String>> pushIn(String segment) {
        List<String> words = List.of(segment.trim().split("\\s+"));
        for (int at = 0; at < words.size(); at++) {
            if (!isGit(words.get(at))) {
                continue;
            }
            int subcommand = afterGitOptions(words, at + 1);
            if (subcommand < words.size() && "push".equals(words.get(subcommand))) {
                return Optional.of(words.subList(subcommand + 1, words.size()));
            }
        }
        return Optional.empty();
    }

    private static int afterGitOptions(List<String> words, int from) {
        int at = from;
        while (at < words.size() && words.get(at).startsWith("-")) {
            at += GIT_OPTION_WITH_VALUE.contains(words.get(at)) ? 2 : 1;
        }
        return at;
    }

    private static boolean isGit(String word) {
        return "git".equals(word) || word.endsWith("/git");
    }

    /**
     * The DESTINATION decides: {@code HEAD:dev} and {@code refs/heads/x:refs/heads/dev} both write dev, whatever
     * they read from. A push naming no ref at all is refused too — it would depend on a config jagt did not
     * write, and the explicit form is what the agent was asked for.
     */
    private static Optional<String> refuse(List<String> arguments, String taskBranch) {
        List<String> refspecs = refspecs(arguments);
        if (arguments.stream().anyMatch(DELETES::contains)
                || refspecs.stream().anyMatch(refspec -> refspec.startsWith(":"))) {
            return Optional.of("jagt refuses deleting a branch from here: the review request of this task is"
                    + " built on it.");
        }
        if (refspecs.isEmpty()) {
            return Optional.of("jagt refuses a push that names no branch: push " + taskBranch + " explicitly."
                    + " This worktree's upstream is detached on purpose.");
        }
        return refspecs.stream().filter(refspec -> !writes(refspec, taskBranch)).findFirst()
                .map(refspec -> "jagt refuses this push: " + destinationOf(refspec) + " is not this task's"
                        + " branch. Only " + taskBranch + " may be pushed from here — a shared branch is written"
                        + " by the human's `deploy`.");
    }

    /**
     * A push carries the shell's own words too — a redirection, a comment, a quoted branch. Reading those as
     * refspecs refused the very push {@code ship} asks for, so the words stop where the command does.
     */
    private static List<String> refspecs(List<String> arguments) {
        List<String> positional = new ArrayList<>();
        boolean valueExpected = false;
        for (String raw : arguments) {
            String argument = unquoted(raw);
            // The shell's own words end the command; a QUOTED word is data, and a branch may be named `#123`.
            boolean shellSyntax = argument.equals(raw)
                    && (argument.startsWith("#") || argument.contains(">") || argument.contains("<")
                    || "&".equals(argument));
            if (argument.isBlank() || shellSyntax) {
                break;
            }
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

    private static boolean writes(String refspec, String taskBranch) {
        String destination = destinationOf(refspec);
        return destination.equals(taskBranch) || destination.equals(CURRENT_BRANCH);
    }

    private static String destinationOf(String refspec) {
        String written = refspec.contains(":") ? refspec.substring(refspec.indexOf(':') + 1) : refspec;
        return written.replaceFirst("^\\+", "").replaceFirst("^refs/heads/", "");
    }
}
