package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.Remote;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The project's own command, run by jagt rather than by the agent that says it passed. An agent's "done and
 * verified" is a claim; this is the measurement, taken before a hand-back is allowed to reach a human.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Verification {

    /** The tail a failing run relays: a whole build log is not a brief. */
    private static final int LINES = 40;

    private final ConfigService configService;
    private final Processes processes;
    private final AcceptanceProbing probing;
    private final WorktreeChanges changes;

    /** What a run is over: the worktree's tree state and the probes declared. EMPTY where git could not read the tree. */
    public Optional<String> key(TaskState task) {
        String probes = task.probes().stream().map(Probe::toString).collect(Collectors.joining("\n"));
        return changes.state(task).map(tree -> Turn.digestOf(tree + "\n" + probes));
    }

    /** The first failure the commands reported: empty means every one of them passed, or none is configured. */
    public Optional<String> failure(TaskState task) {
        return task.repos().stream().map(this::failureOf).flatMap(Optional::stream).findFirst();
    }

    /** Each probe the ratified plan declared, with its result, run in the repository it names; the task's id is its branch. */
    public List<Probe> probed(String taskId, TaskState task) {
        return task.probes().stream().map(probe -> probedIn(taskId, task, probe)).toList();
    }

    private Probe probedIn(String taskId, TaskState task, Probe probe) {
        String project = probe.repo() == null ? task.project() : probe.repo();
        String worktree = task.repos().stream().filter(repo -> Objects.equals(repo.project(), project)).findFirst()
                .map(TaskRepo::worktreePath).orElse(task.worktreePath());
        return probing.probed(taskId, Path.of(worktree), remoteOf(project), List.of(probe)).getFirst();
    }

    private Remote remoteOf(@Nullable String project) {
        ProjectConfig config = project == null ? null : configService.load().projects().get(project);
        return config == null ? Remote.ORIGIN : config.gitRemote();
    }

    private Optional<String> failureOf(TaskRepo repo) {
        ProjectConfig project = configService.load().projects().get(repo.project());
        List<String> command = command(project);
        if (project == null || command.isEmpty()) {
            return Optional.empty();
        }
        Processes.Result result = processes.run(Path.of(repo.worktreePath()), project.verifyTimeoutOrDefault(), command);
        if (result.exitCode() == 0) {
            return Optional.empty();
        }
        log.atInfo().setMessage("verification failed")
                .addKeyValue("project", repo.project())
                .addKeyValue("command", String.join(" ", command))
                .addKeyValue("exit", result.exitCode())
                .log();
        return Optional.of("[" + repo.project() + "] `" + String.join(" ", command) + "` exited "
                + result.exitCode() + "\n" + tail(result.stderr().isBlank() ? result.stdout() : result.stderr()));
    }

    /** A project the configuration no longer names runs nothing, rather than failing the report that asks. */
    static List<String> command(@Nullable ProjectConfig project) {
        return project == null || project.verifyCommand() == null ? List.of() : project.verifyCommand();
    }

    private static String tail(String output) {
        String[] lines = output.strip().split("\n");
        return lines.length <= LINES ? output.strip()
                : String.join("\n", List.of(lines).subList(lines.length - LINES, lines.length));
    }
}
