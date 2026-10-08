package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.service.GitDeploy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

final class E2eWorkspace {

    static final String TMUX_SESSION = "jagt-e2e-" + UUID.randomUUID().toString().substring(0, 8);

    private E2eWorkspace() {
    }

    static void awaitAFullRunStartedAfterNow(Jobs jobs, String job) {
        long since = System.currentTimeMillis();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).until(() -> {
            try {
                jobs.runNow(job);
                return true;
            } catch (IllegalStateException running) {
                return false;
            }
        });
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100))
                .until(() -> jobs.statuses(since).stream().filter(status -> status.id().equals(job))
                        .anyMatch(status -> !status.running() && status.lastStartedAt() != null
                                && status.lastStartedAt() >= since));
    }

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

    static String remoteUrl(Path origin) {
        return "file://" + origin;
    }

    static String requestUrl(Path origin) {
        return "https://code.example/" + GitRemote.projectPath(remoteUrl(origin)) + "/-/merge_requests/1";
    }

    static void createRootMarker(Path root) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("mcp_client.js"), "// e2e placeholder proxy\n");
    }

    static void writeConfig(Path configFile, Path projectPath, TaskFlowCase flowCase) throws IOException {
        writeConfig(configFile, projectPath, flowCase.viewMode(), flowCase.autoReview());
    }

    static void writeConfig(Path configFile, Path projectPath, String viewMode, boolean autoReview)
            throws IOException {
        writeConfig(configFile, new LinkedHashMap<>(Map.of("proj", projectPath)), viewMode, autoReview);
    }

    static void writeConfig(Path configFile, Path projectPath, MasterModeCase master) throws IOException {
        writeConfig(configFile, new LinkedHashMap<>(Map.of("proj", projectPath)), "shared", false);
        Files.writeString(configFile, """
                  master: { mode: "%s", mine: [%s] }
                """.formatted(master.mode(), String.join(", ", master.mine())), StandardOpenOption.APPEND);
    }

    static void writeConfig(Path configFile, Map<String, Path> projects, String viewMode, boolean autoReview)
            throws IOException {
        Files.createDirectories(configFile.getParent());
        String configured = projects.entrySet().stream()
                .map(project -> """
                        %s: { path: "%s", baseBranch: origin/main, deployBranch: dev }\
                        """.formatted(project.getKey(), project.getValue()))
                .collect(Collectors.joining("\n    "));
        Files.writeString(configFile, """
                orchestrator:
                  projects:
                    %s
                  viewer: { tmuxSession: "%s", viewMode: "%s" }
                  autoReview: { enabled: %s }
                """.formatted(configured, TMUX_SESSION, viewMode, autoReview));
    }

    static void resetDeployBranch(Path repo) {
        gitQuietly(repo, "push", "--force", "origin", "origin/main:refs/heads/dev");
    }

    static void commitOnDeployBranch(Path repo, String file, String content) throws Exception {
        git(repo, "fetch", "origin");
        git(repo, "checkout", "-B", "e2e-deploy-side", "origin/dev");
        Files.writeString(repo.resolve(file), content);
        git(repo, "add", file);
        git(repo, "commit", "-m", "Someone else changed " + file);
        git(repo, "push", "origin", "e2e-deploy-side:dev");
        git(repo, "checkout", "main");
    }

    static void forgetTask(Path repo, Path worktree, String branch) {
        gitQuietly(repo, "worktree", "remove", "--force", worktree.toString());
        gitQuietly(repo, "worktree", "remove", "--force",
                GitDeploy.deployWorktreePath(repo, branch).toString());
        gitQuietly(repo, "worktree", "remove", "--force",
                GitDeploy.revertWorktreePath(repo, branch).toString());
        gitQuietly(repo, "worktree", "prune");
        gitQuietly(repo, "branch", "-D", branch);
        gitQuietly(repo, "branch", "-D", GitDeploy.deployBranch(branch));
        gitQuietly(repo, "branch", "-D", "jagt-revert-" + branch);
        gitQuietly(repo, "push", "origin", "--delete", branch);
    }

    static void killTmuxSessions(String tmuxCommand) {
        String listed = run(tmuxCommand, "list-sessions", "-F", "#{session_name}");
        for (String session : own(listed == null ? "" : listed)) {
            run(tmuxCommand, "kill-session", "-t", "=" + session);
        }
    }

    static List<String> tmuxSessions(String tmuxCommand) {
        String listed = run(tmuxCommand, "list-sessions", "-F", "#{session_name}");
        if (listed == null) {
            throw new IllegalStateException("tmux sessions could not be read from '" + tmuxCommand
                    + "' (resolved to " + Executables.resolve(tmuxCommand) + "): not runnable, refused, or the"
                    + " wait was interrupted. No session can be asserted from that.");
        }
        return own(listed);
    }

    private static List<String> own(String listed) {
        return listed.lines().filter(name -> name.startsWith(TMUX_SESSION)).toList();
    }

    private static String run(String command, String... args) {
        List<String> full = new java.util.ArrayList<>(List.of(Executables.resolve(command)));
        full.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
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
