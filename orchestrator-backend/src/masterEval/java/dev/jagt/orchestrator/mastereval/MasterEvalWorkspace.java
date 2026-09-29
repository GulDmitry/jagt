package dev.jagt.orchestrator.mastereval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** A throwaway repository per round: a committed baseline, and the case's change left uncommitted in a worktree. */
final class MasterEvalWorkspace {

    static final String TMUX_SESSION = "jagt-master-eval";

    private MasterEvalWorkspace() {
    }

    /** Answers the worktree the round is to be reviewed in. */
    static Path worktreeFor(Path repo, MasterCase round, String taskId) throws Exception {
        Files.createDirectories(repo);
        git(repo, "init", "--initial-branch=main", ".");
        git(repo, "config", "user.email", "eval@example.com");
        git(repo, "config", "user.name", "jagt master eval");
        write(repo, round.baseline());
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "Baseline");
        Path worktree = repo.getParent().resolve(taskId);
        git(repo, "worktree", "add", "-b", taskId, worktree.toString(), "main");
        write(worktree, round.change());
        return worktree;
    }

    static void writeConfig(Path configFile, Path projectPath, String brief) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
                orchestrator:
                  projects:
                    proj: { path: "%s", baseBranch: main }
                  viewer: { tmuxSession: "%s" }
                  master: { mode: judge, brief: "%s" }
                """.formatted(projectPath, TMUX_SESSION, brief));
    }

    private static void write(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
    }

    static void git(Path directory, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", arguments) + " failed in " + directory
                    + ": " + new String(process.getInputStream().readAllBytes()));
        }
    }
}
