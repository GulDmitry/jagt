package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The project's own command, run by jagt rather than by the agent that says it passed. An agent's "done and
 * verified" is a claim; this is the measurement, taken before a hand-back is allowed to reach a human.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Verification {

    /** Long enough for a real suite, since a timeout reads as a failure the agent then cannot reproduce. */
    private static final Duration LIMIT = Duration.ofMinutes(20);

    /** The tail a failing run relays: a whole build log is not a brief. */
    private static final int LINES = 40;

    private final ConfigService configService;
    private final Processes processes;

    /** Whether any repository of this task ships a command at all. */
    public boolean configured(TaskState task) {
        return task.repos().stream().anyMatch(repo -> !command(repo).isEmpty());
    }

    /** What the commands said, worst first: empty means every one of them passed, or none is configured. */
    public Optional<String> failure(TaskState task) {
        return task.repos().stream().map(this::failureOf).flatMap(Optional::stream).findFirst();
    }

    private Optional<String> failureOf(TaskRepo repo) {
        List<String> command = command(repo);
        if (command.isEmpty()) {
            return Optional.empty();
        }
        Processes.Result result = processes.run(Path.of(repo.worktreePath()), LIMIT, command);
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
    private List<String> command(TaskRepo repo) {
        ProjectConfig project = configService.project(repo.project());
        return project == null || project.verifyCommand() == null ? List.of() : project.verifyCommand();
    }

    private static String tail(String output) {
        String[] lines = output.strip().split("\n");
        return lines.length <= LINES ? output.strip()
                : String.join("\n", List.of(lines).subList(lines.length - LINES, lines.length));
    }
}
