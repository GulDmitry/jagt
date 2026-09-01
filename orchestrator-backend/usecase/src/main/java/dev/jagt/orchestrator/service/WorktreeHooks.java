package dev.jagt.orchestrator.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.nio.file.attribute.PosixFilePermission;

/**
 * The push guard as git itself answers it: a hook jagt writes into the worktree it cut, reached only by the session
 * it launches. Nothing is written to the repository — the scripts live under the worktree's own {@code .jagt/} and
 * the session's git is pointed at them by {@link #gitEnv} on the launch command alone.
 *
 * <p>Pointing git at another directory REPLACES the repository's hooks rather than adding to them, so every name
 * git knows is re-exposed here as a stub that runs the repository's own. The whole list, not what a repository held
 * when its worktree was cut: a session that installs husky an hour in must not lose it.
 *
 * <p>A guardrail, not a boundary: {@code --no-verify} skips it as it skips any hook.
 */
@Slf4j
public final class WorktreeHooks {

    /** Under a name a commit already refuses to carry and {@code git status} already hides. */
    private static final Path DIRECTORY = Path.of(".jagt", "hooks");
    private static final String PUSH_HOOK = "pre-push";
    /** Where the repository being pushed keeps its own hooks, asked with the override off. */
    private static final String PROJECT_HOOKS =
            "$(GIT_CONFIG_COUNT=0 git rev-parse --path-format=absolute --git-path hooks 2>/dev/null)";
    private static final Set<PosixFilePermission> EXECUTABLE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    /** Every name git will look for, from {@code githooks(5)} — a stub for one nobody has costs a file. */
    private static final List<String> GIT_HOOKS = List.of("applypatch-msg", "pre-applypatch", "post-applypatch",
            "pre-commit", "pre-merge-commit", "prepare-commit-msg", "commit-msg", "post-commit", "pre-rebase",
            "post-checkout", "post-merge", "pre-receive", "update", "proc-receive", "post-receive",
            "post-update", "reference-transaction", "push-to-checkout", "pre-auto-gc", "post-rewrite",
            "sendemail-validate", "fsmonitor-watchman", "p4-changelist", "p4-prepare-changelist",
            "p4-post-changelist", "p4-pre-submit", "post-index-change");

    private WorktreeHooks() {
    }

    public static void install(Path worktree, String taskBranch) {
        Path directory = worktree.resolve(DIRECTORY);
        try {
            Files.createDirectories(directory);
            write(directory.resolve(PUSH_HOOK), pushGuard(taskBranch));
            for (String name : GIT_HOOKS) {
                write(directory.resolve(name), passThrough(name));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the push guard into " + directory, e);
        }
    }

    /**
     * The assignment that puts a session's git on those hooks, as one shell prefix. Scoped to the launched process
     * rather than written anywhere. Empty where the worktree holds no such directory — an override pointing nowhere
     * runs NO hook at all.
     */
    public static String gitEnv(Path worktree) {
        Path directory = worktree.resolve(DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return "";
        }
        return "GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.hooksPath GIT_CONFIG_VALUE_0="
                + quoted(directory.toString()) + " ";
    }

    /**
     * Reads the refs git is about to write, one per line, and refuses every destination that is not the task's own
     * branch. The repository's own hook runs after the guard, on the same input.
     */
    private static String pushGuard(String taskBranch) {
        return """
                #!/bin/sh
                branch=%s
                project_hook=%s/pre-push
                refs=$(cat)
                while read -r local_ref local_sha remote_ref remote_sha; do
                    [ -n "$remote_ref" ] || continue
                    if [ "$remote_ref" != "refs/heads/$branch" ]; then
                        echo "jagt refuses this push: $remote_ref is not this task's branch." >&2
                        echo "Only $branch may be pushed from this worktree — a shared branch is written by\
                 the human's deploy." >&2
                        exit 1
                    fi
                    case "$local_sha" in
                        *[!0]*) ;;
                        *)
                            echo "jagt refuses deleting $remote_ref: the review request of this task is built\
                 on it." >&2
                            exit 1
                            ;;
                    esac
                done <<REFS
                $refs
                REFS
                if [ -x "$project_hook" ]; then
                    if [ -n "$refs" ]; then
                        printf '%%s\\n' "$refs" | GIT_CONFIG_COUNT=0 "$project_hook" "$@"
                    else
                        GIT_CONFIG_COUNT=0 "$project_hook" "$@" < /dev/null
                    fi
                    exit $?
                fi
                exit 0
                """.formatted(quoted(taskBranch), PROJECT_HOOKS);
    }

    /** The override goes off for what it runs, or a project hook running git would come straight back here. */
    private static String passThrough(String name) {
        return """
                #!/bin/sh
                hook=%s/%s
                [ -x "$hook" ] || exit 0
                GIT_CONFIG_COUNT=0
                export GIT_CONFIG_COUNT
                exec "$hook" "$@"
                """.formatted(PROJECT_HOOKS, name);
    }

    private static void write(Path script, String content) throws IOException {
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, EXECUTABLE);
    }

    private static String quoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
