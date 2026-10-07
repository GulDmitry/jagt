package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.adapter.agent.MasterEvalTranscripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** A throwaway repository per round: a committed baseline, and the case's change left uncommitted in a worktree. */
final class MasterEvalWorkspace {

    static final String TMUX_SESSION = "jagt-master-eval";
    /**
     * A FIXED root rather than a temp one, and the reason is trust: an agent CLI refuses to work in a
     * directory nobody has said it may, and a fresh path every run would be asked about every run. Trusted
     * once, by a human, and nothing here forges that.
     */
    private static final Path ROOT = Path.of("..", ".master-eval").toAbsolutePath().normalize();

    private MasterEvalWorkspace() {
    }

    static Path root() {
        return ROOT.resolve("root");
    }

    /**
     * Refuses while another run of this suite is alive. Two of them share one tmux session and one Master
     * window, and each would type its own rounds at the other's reviewer — which reads as a reviewer answering
     * about a task it was never given. Beside the tree rather than inside it: {@link #clean} empties that.
     */
    static void claim() throws IOException {
        Path lock = ROOT.resolveSibling(".master-eval.lock");
        if (Files.isRegularFile(lock) && alive(Files.readString(lock).strip())) {
            throw new IllegalStateException("Another masterEval run holds " + lock + " — one session cannot"
                    + " serve two. Wait for it, or end it and delete that file.");
        }
        Files.createDirectories(lock.getParent());
        Files.writeString(lock, String.valueOf(ProcessHandle.current().pid()));
    }

    static void release() throws IOException {
        Files.deleteIfExists(ROOT.resolveSibling(".master-eval.lock"));
    }

    private static boolean alive(String pid) {
        try {
            return ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException unreadable) {
            return false;
        }
    }

    /** Between runs, since the rounds are rebuilt and a worktree left behind would be reviewed again. */
    static void clean() throws IOException {
        if (!Files.exists(ROOT)) {
            return;
        }
        try (var walk = Files.walk(ROOT)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException leftBehind) {
                    throw new java.io.UncheckedIOException(leftBehind);
                }
            });
        }
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
        // jagt reads a round against origin/<base>, as every real task has one.
        Path origin = repo.getParent().resolve("origin.git");
        git(repo.getParent(), "init", "--bare", "--initial-branch=main", origin.toString());
        git(repo, "remote", "add", "origin", origin.toString());
        git(repo, "push", "origin", "main");
        git(repo, "fetch", "origin");
        Path worktree = repo.getParent().resolve(taskId);
        git(repo, "worktree", "add", "-b", taskId, worktree.toString(), "main");
        write(worktree, round.change());
        // What a launch leaves for a task nobody filed, kept out of the diff as jagt's own plumbing is.
        Files.writeString(worktree.resolve("task_request.md"), round.instructions());
        if (!round.plan().isBlank()) {
            Files.writeString(worktree.resolve("plan.md"), round.plan());
        }
        Files.writeString(repo.resolve(".git/info/exclude"), "task_request.md\nplan.md\n");
        MasterEvalTranscripts.nothingTypedIn(worktree);
        return worktree;
    }

    static void writeConfig(Path configFile, Path projectPath, String brief) throws IOException {
        writeConfig(configFile, projectPath, brief, "judge");
    }

    static void writeConfig(Path configFile, Path projectPath, String brief, String mode) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
                orchestrator:
                  projects:
                    proj: { path: "%s", baseBranch: main }
                  viewer: { tmuxSession: "%s" }
                  master: { mode: %s, brief: "%s", model: "%s" }
                """.formatted(projectPath, TMUX_SESSION, mode, brief, System.getProperty("masterEval.model", "")));
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
