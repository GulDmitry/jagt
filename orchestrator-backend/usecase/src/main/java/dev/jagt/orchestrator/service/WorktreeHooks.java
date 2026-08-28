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
 * The push guard as git itself answers it: a hook jagt writes into the worktree it cut, reached only by the
 * session it launches.
 *
 * <p>Nothing is written to the repository. The scripts live under the worktree's own {@code .jagt/}, and the
 * session's git is pointed at them by {@link #gitEnv} on the launch command alone — so jagt's own git, the
 * human's shell and every other checkout of that repository resolve hooks exactly as they did before.
 *
 * <p>It answers where reading a command line cannot: a push assembled at runtime, an alias, a script the agent
 * wrote, and a CLI whose own hooks jagt has nothing wired into. The destination it reads is the one git is
 * about to write.
 *
 * <p>Pointing git at another directory REPLACES the repository's hooks rather than adding to them, so every
 * hook the task's repositories have is re-exposed here as a stub that runs the original.
 *
 * <p>A guardrail, not a boundary: {@code --no-verify} skips it, as it skips any hook, and so does turning the
 * override off. What it catches is the push nobody meant to make; what stops a determined one is that a shared
 * branch is written by a human.
 */
@Slf4j
public final class WorktreeHooks {

    /** Under a name a commit already refuses to carry and {@code git status} already hides. */
    private static final Path DIRECTORY = Path.of(".jagt", "hooks");
    private static final String PUSH_HOOK = "pre-push";
    /**
     * Where the repository being pushed keeps its own hooks, asked with the override off — a session works in
     * one directory of scripts and in as many repositories as the task has.
     */
    private static final String PROJECT_HOOKS =
            "$(GIT_CONFIG_COUNT=0 git rev-parse --path-format=absolute --git-path hooks 2>/dev/null)";
    private static final String SAMPLE_SUFFIX = ".sample";
    private static final Set<PosixFilePermission> EXECUTABLE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    private WorktreeHooks() {
    }

    /**
     * @param projectHooks where the task's repositories keep their own hooks, read for the NAMES they hold —
     *                     which of them have to stay reachable. One directory of scripts serves a whole session,
     *                     so a name any repository of the task has needs a stub here; each script finds the
     *                     directory of the repository it is running in again for itself.
     */
    public static void install(Path worktree, List<Path> projectHooks, String taskBranch) {
        Path directory = worktree.resolve(DIRECTORY);
        try {
            Files.createDirectories(directory);
            write(directory.resolve(PUSH_HOOK), pushGuard(taskBranch));
            for (String name : projectHookNames(projectHooks)) {
                write(directory.resolve(name), passThrough(name));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the push guard into " + directory, e);
        }
    }

    /**
     * The assignment that puts a session's git on those hooks, as one shell prefix. Scoped to the launched
     * process rather than written anywhere: what jagt refuses an agent it must not also refuse the human at
     * the same checkout, nor itself.
     *
     * <p>Empty where the worktree holds no such directory — an override pointing nowhere runs NO hook, and
     * silently taking the project's own away is worse than a session with no guard.
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
     * Reads the refs git is about to write, one per line, and refuses every destination that is not the task's
     * own branch. The repository's own hook runs after the guard, on the same input: a push this refuses is
     * one it never had to see.
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

    /**
     * The override goes off for what it runs: a project hook that itself runs git would otherwise come straight
     * back here, and a hook that commits would do so forever.
     */
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

    private static Set<String> projectHookNames(List<Path> projectHooks) {
        return projectHooks.stream().filter(Files::isDirectory)
                .flatMap(WorktreeHooks::executableNamesIn)
                .filter(name -> !name.endsWith(SAMPLE_SUFFIX))
                .filter(name -> !name.equals(PUSH_HOOK))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Stream<String> executableNamesIn(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .filter(Files::isExecutable)
                    .map(hook -> hook.getFileName().toString())
                    .toList().stream();
        } catch (IOException e) {
            log.atWarn().setMessage("project hooks unreadable")
                    .addKeyValue("path", directory)
                    .addKeyValue("cause", e.getMessage())
                    .log();
            return Stream.empty();
        }
    }

    private static void write(Path script, String content) throws IOException {
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, EXECUTABLE);
    }

    private static String quoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
