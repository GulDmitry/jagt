package dev.jagt.orchestrator.mastereval;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** A throwaway repository per round: a committed baseline, and the case's change left uncommitted in a worktree. */
final class MasterEvalWorkspace {

    static final String TMUX_SESSION = "jagt-master-eval";
    /**
     * A FIXED root rather than a temp one, and the reason is trust: an agent CLI refuses to start in a
     * directory nobody has said it may work in, and a fresh path every run would ask again every run. This one
     * is trusted once, by a human, and nothing here forges that.
     */
    private static final Path ROOT = Path.of("..", ".master-eval").toAbsolutePath().normalize();

    private MasterEvalWorkspace() {
    }

    static Path root() {
        return ROOT.resolve("root");
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

    /**
     * Whether a human has told the agent CLI it may work in {@code directory}. READ ONLY: that file is written
     * by every session the human has open, and a suite rewriting it would race all of them.
     */
    static boolean trusted(Path directory) throws IOException {
        Path config = Path.of(System.getProperty("user.home"), ".claude.json");
        if (!Files.isRegularFile(config)) {
            return false;
        }
        return new JsonMapper().readTree(Files.readString(config))
                .path("projects").path(directory.toString()).path("hasTrustDialogAccepted").asBoolean(false);
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
