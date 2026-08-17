package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.model.GitRemote;
import dev.jagt.orchestrator.service.GitService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The throwaway outside world an e2e run needs: a local origin + a clone to cut worktrees from, the
 * orchestrator root markers, a {@code config.json} written per matrix combination, and the tmux session the
 * run is allowed to kill afterwards. Nothing here reaches beyond the given temp directory — no network, no
 * remote host, no file of the developer's.
 */
final class E2eWorkspace {

    /** Never a name a human would use for real work, because the run kills this session on the way out. */
    static final String TMUX_SESSION = "jagt-e2e";

    private E2eWorkspace() {
    }

    /**
     * A bare origin plus a clone with one commit on {@code main}, pushed — the shape {@code createWorktree}
     * expects (it cuts task branches from {@code origin/<baseBranch>}). The deploy branch exists on the origin
     * ONLY: nothing checks it out in a real setup either, and a deploy resolves it through {@code origin/}.
     */
    static void createRepositoryWithOrigin(Path origin, Path repo) throws Exception {
        Files.createDirectories(origin);
        Files.createDirectories(repo);
        git(origin, "init", "--bare", "--initial-branch=main", ".");
        git(repo, "init", "--initial-branch=main", ".");
        git(repo, "config", "user.email", "e2e@example.com");
        git(repo, "config", "user.name", "jagt e2e");
        Files.writeString(repo.resolve("README.md"), "e2e fixture\n");
        git(repo, "add", "README.md");
        git(repo, "commit", "-m", "Initial commit");
        git(repo, "remote", "add", "origin", remoteUrl(origin));
        git(repo, "push", "-u", "origin", "main");
        git(repo, "push", "origin", "main:refs/heads/dev");
    }

    /** A URL rather than the bare path it wraps: only a URL carries a project that can be matched. */
    static String remoteUrl(Path origin) {
        return "file://" + origin;
    }

    /** A request URL carrying the repository's own project, which is how a resumed request finds it. */
    static String requestUrl(Path origin) {
        return "https://code.example/" + GitRemote.projectPath(remoteUrl(origin)) + "/-/merge_requests/1";
    }

    /** The orchestrator root is detected by this marker, and every worktree links it. */
    static void createRootMarker(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// e2e placeholder proxy\n");
    }

    static void writeConfig(Path configFile, Path projectPath, TaskFlowCase flowCase) throws IOException {
        writeConfig(configFile, projectPath, flowCase.viewMode(), flowCase.autoReview());
    }

    static void writeConfig(Path configFile, Path projectPath, String viewMode, boolean autoReview)
            throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
                {
                  "projects": {
                    "proj": {
                      "path": "%s",
                      "baseBranch": "origin/main",
                      "deployBranch": "dev"
                    }
                  },
                  "viewer": { "tmuxSession": "%s", "viewMode": "%s" },
                  "autoReview": { "enabled": %s }
                }
                """.formatted(projectPath, TMUX_SESSION, viewMode, autoReview));
    }

    /**
     * Unregisters a task's branches everywhere a run could have left them — including the origin, since the
     * next case pushes the same name and an unrelated history is refused, not forced. A deploy that conflicted
     * KEEPS its worktree on purpose, and a case that failed mid-deploy would hand that half-state to the next
     * one, so those go too. Best-effort: a case that failed early may hold nothing, and a cleanup failure must
     * not mask the real one.
     *
     * <p>What it does NOT undo is the deploy branch itself: a merge and its revert stay on it, so a second
     * deploying case needs a repository of its own rather than this teardown.
     */
    static void forgetTask(Path repo, Path worktree, String branch) {
        gitQuietly(repo, "worktree", "remove", "--force", worktree.toString());
        gitQuietly(repo, "worktree", "remove", "--force",
                GitService.deployWorktreePath(repo, branch).toString());
        gitQuietly(repo, "worktree", "remove", "--force",
                GitService.revertWorktreePath(repo, branch).toString());
        gitQuietly(repo, "worktree", "prune");
        gitQuietly(repo, "branch", "-D", branch);
        gitQuietly(repo, "branch", "-D", "jagt-deploy-" + branch);
        gitQuietly(repo, "branch", "-D", "jagt-revert-" + branch);
        gitQuietly(repo, "push", "origin", "--delete", branch);
    }

    /**
     * Kills every session the run could have created — by PREFIX, because {@code tab-per-task} puts each task
     * in a session of its own ({@code <session>-<taskId>}) and killing only the configured name leaves those
     * behind. Best-effort: the session may never have existed, and cleanup must not fail a green run.
     */
    static void killTmuxSessions(String tmuxCommand) {
        for (String session : run(tmuxCommand, "list-sessions", "-F", "#{session_name}").lines()
                .filter(name -> name.startsWith(TMUX_SESSION)).toList()) {
            run(tmuxCommand, "kill-session", "-t", "=" + session);
        }
    }

    private static String run(String command, String... args) {
        List<String> full = new java.util.ArrayList<>(List.of(command));
        full.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(10, TimeUnit.SECONDS);
            return output;
        } catch (IOException e) {
            return "";   // tmux absent — then the run had no session to leave behind either.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static void gitQuietly(Path cwd, String... args) {
        try {
            git(cwd, args);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    static String git(Path cwd, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed in " + cwd + ": " + output);
        }
        return output;
    }
}
