package dev.jagt.orchestrator.e2e;

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
     * expects (it cuts task branches from {@code origin/<baseBranch>}).
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
        git(repo, "remote", "add", "origin", origin.toString());
        git(repo, "push", "-u", "origin", "main");
    }

    /** The orchestrator root is detected by this marker, and every worktree links it. */
    static void createRootMarker(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// e2e placeholder proxy\n");
    }

    static void writeConfig(Path configFile, Path projectPath, TaskFlowCase flowCase) throws IOException {
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
                """.formatted(projectPath, TMUX_SESSION, flowCase.viewMode(), flowCase.autoReview()));
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
