package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.adapter.agent.MasterEvalTranscripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
final class MasterEvalWorkspace {

    static final String TMUX_SESSION = "jagt-master-eval";
    private static final Path FIXED_ROOT_A_HUMAN_TRUSTED_ONCE = Path.of("..", ".master-eval").toAbsolutePath().normalize();

    private MasterEvalWorkspace() {
    }

    static Path root() {
        return FIXED_ROOT_A_HUMAN_TRUSTED_ONCE.resolve("root");
    }
    static void claim() throws IOException {
        Path lock = FIXED_ROOT_A_HUMAN_TRUSTED_ONCE.resolveSibling(".master-eval.lock");
        if (Files.isRegularFile(lock) && alive(Files.readString(lock).strip())) {
            throw new IllegalStateException("Another masterEval run holds " + lock + " — one session cannot"
                    + " serve two. Wait for it, or end it and delete that file.");
        }
        Files.createDirectories(lock.getParent());
        Files.writeString(lock, String.valueOf(ProcessHandle.current().pid()));
    }

    static void release() throws IOException {
        Files.deleteIfExists(FIXED_ROOT_A_HUMAN_TRUSTED_ONCE.resolveSibling(".master-eval.lock"));
    }

    private static boolean alive(String pid) {
        try {
            return ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException unreadable) {
            return false;
        }
    }
    static void clean() throws IOException {
        if (!Files.exists(FIXED_ROOT_A_HUMAN_TRUSTED_ONCE)) {
            return;
        }
        try (var walk = Files.walk(FIXED_ROOT_A_HUMAN_TRUSTED_ONCE)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException leftBehind) {
                    throw new java.io.UncheckedIOException(leftBehind);
                }
            });
        }
    }
    static Path worktreeFor(Path repo, MasterCase round, String taskId) throws Exception {
        Files.createDirectories(repo);
        git(repo, "init", "--initial-branch=main", ".");
        git(repo, "config", "user.email", "eval@example.com");
        git(repo, "config", "user.name", "jagt master eval");
        write(repo, round.baseline());
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "Baseline");
        Path origin = repo.getParent().resolve("origin.git");
        git(repo.getParent(), "init", "--bare", "--initial-branch=main", origin.toString());
        git(repo, "remote", "add", "origin", origin.toString());
        git(repo, "push", "origin", "main");
        git(repo, "fetch", "origin");
        Path worktree = repo.getParent().resolve(taskId);
        git(repo, "worktree", "add", "-b", taskId, worktree.toString(), "main");
        write(worktree, round.change());
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
