package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.Probe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Runs a task's probes in its worktree, under the guard and the git hooks its session's own lines get. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AcceptanceProbing {

    private static final Duration LIMIT = Duration.ofMinutes(5);
    private static final int LINES = 20;
    private static final int CHARACTERS = 2_000;

    private final Processes processes;
    @Value("${server.port:8290}")
    private final int boardPort;

    public List<Probe> probed(String taskBranch, Path worktree, List<Probe> probes) {
        return probes.stream().map(probe -> probe(taskBranch, worktree, probe)).toList();
    }

    private Probe probe(String taskBranch, Path worktree, Probe probe) {
        return switch (probe.kind()) {
            case COMMAND -> ToolGate.refusal(String.join(" ", probe.words()), taskBranch, boardPort)
                    .map(refused -> probe.probed(false, refused))
                    .orElseGet(() -> run(worktree, probe, probe.words()));
            case REF -> run(worktree, probe, List.of("git", "rev-parse", "--verify", "--quiet",
                    probe.words().getFirst()));
            case ARTIFACT -> artifact(worktree, probe);
        };
    }

    private Probe run(Path worktree, Probe probe, List<String> command) {
        Map<String, String> env = WorktreeHooks.gitEnvironment(worktree);
        try {
            Processes.Result result = processes.run(worktree, LIMIT, env, command);
            boolean passed = result.exitCode() == probe.exit();
            if (!passed) {
                log.atInfo().setMessage("probe failed").addKeyValue("cmd", probe.toString())
                        .addKeyValue("exit", result.exitCode()).log();
            }
            return probe.probed(passed, "exit " + result.exitCode() + excerpt(result.stdout() + "\n" + result.stderr()));
        } catch (IllegalStateException failed) {
            log.atInfo().setMessage("probe failed").addKeyValue("cmd", probe.toString())
                    .addKeyValue("cause", failed.getMessage()).log();
            return probe.probed(false, excerpt(String.valueOf(failed.getMessage())).strip());
        }
    }

    /** A link out of the worktree is no file in it. */
    private static Probe artifact(Path worktree, Probe probe) {
        try {
            boolean inside = worktree.resolve(probe.words().getFirst()).toRealPath().startsWith(worktree.toRealPath());
            return probe.probed(inside, inside ? "" : "it resolves outside the worktree");
        } catch (NoSuchFileException missing) {
            return probe.probed(false, "no such file");
        } catch (IOException unreadable) {
            return probe.probed(false, "jagt could not read it: " + unreadable);
        }
    }

    private static String excerpt(String output) {
        List<String> lines = output.strip().lines().toList();
        String tail = String.join("\n", lines.subList(Math.max(0, lines.size() - LINES), lines.size()));
        String capped = tail.length() <= CHARACTERS ? tail : tail.substring(tail.length() - CHARACTERS);
        return Optional.of(capped).filter(text -> !text.isBlank()).map(text -> "\n" + text).orElse("");
    }
}
