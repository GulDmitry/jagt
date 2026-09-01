package dev.jagt.orchestrator.task;

import java.util.regex.Pattern;

/**
 * A task IS its branch, so anything git accepts as a branch name is a task name — the rules of
 * {@code git check-ref-format --branch} in one pattern. Such a name may still not be a file or a tmux session:
 * `/` nests a directory and a quote reaches a shell, so {@link #slug(String)} is the flat form.
 */
public final class TaskName {

    /** One directory component holds 255 bytes, and the slug is followed by `-<project key>-deploy`. */
    private static final int MAX = 180;

    /** What {@code git check-ref-format} refuses outright, on top of every control character and the space. */
    private static final String REFUSED_BY_GIT = "~^:?*[\\";
    private static final String REFNAME_CHAR = "[^\\x00-\\x20\\x7f" + Pattern.quote(REFUSED_BY_GIT) + "]";

    private static final Pattern VALID = Pattern.compile(
            "(?!-)(?!/)(?!@$)(?!HEAD$)(?!.*(^|/)\\.)(?!.*\\.lock(/|$))(?!.*\\.\\.)(?!.*@\\{)(?!.*//)"
                    + REFNAME_CHAR + "{1," + MAX + "}(?<![./])");
    private static final Pattern NOT_PLAIN = Pattern.compile("[^A-Za-z0-9_-]");

    private TaskName() {
    }

    private static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    /**
     * The same task as ONE plain name: a worktree directory, a tmux session, a socket, a temp file. A dot goes
     * too — tmux addresses a window as {@code session:window.pane}.
     */
    public static String slug(String name) {
        return NOT_PLAIN.matcher(name).replaceAll("-");
    }

    public static void require(String name, String argument) {
        String reason = unusableReason(name);
        if (reason != null) {
            throw new IllegalArgumentException("Argument '" + argument + "' is not a branch name, and a task"
                    + " IS its branch: " + reason + "; got: " + name);
        }
    }

    /** Names the ONE thing that makes {@code name} unusable, or null when it is usable. */
    public static String unusableReason(String name) {
        if (isValid(name)) {
            return null;
        }
        if (name == null || name.isEmpty()) {
            return "it is empty";
        }
        String offender = name.codePoints()
                .filter(TaskName::refusedByGit)
                .mapToObj(TaskName::readable)
                .findFirst().orElse(null);
        if (offender != null) {
            return "'" + offender + "' is not allowed";
        }
        if (name.startsWith("-")) {
            return "it starts with '-'";
        }
        if (name.startsWith("/") || name.endsWith("/")) {
            return "it starts or ends with '/'";
        }
        if (name.contains("//")) {
            return "it has an empty part ('//')";
        }
        if (name.endsWith(".")) {
            return "it ends with '.'";
        }
        if (name.contains("..")) {
            return "'..' is not allowed";
        }
        if (name.startsWith(".") || name.contains("/.")) {
            return "a part of it starts with '.'";
        }
        if (name.endsWith(".lock") || name.contains(".lock/")) {
            return "a part of it ends with '.lock'";
        }
        if (name.contains("@{")) {
            return "'@{' is not allowed";
        }
        if (name.length() > MAX) {
            return "it is longer than " + MAX + " characters";
        }
        return "git reserves that name";
    }

    private static boolean refusedByGit(int codePoint) {
        return codePoint <= 0x20 || codePoint == 0x7f || REFUSED_BY_GIT.indexOf(codePoint) >= 0;
    }

    private static String readable(int codePoint) {
        return codePoint < 0x20 || codePoint == 0x7f
                ? "\\u%04x".formatted(codePoint) : Character.toString(codePoint);
    }
}
