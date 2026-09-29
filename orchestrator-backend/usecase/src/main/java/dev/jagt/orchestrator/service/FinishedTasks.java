package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.FinishedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * One line per retired task, beside `state.json`. Append-only and never read back by jagt to decide anything, so
 * a line that cannot be written is logged rather than thrown: losing a measurement must not refuse a `done`.
 * It does not grow forever unwatched either: past {@link #MAX_BYTES} it keeps appending and says so.
 */
@Service
@Slf4j
public class FinishedTasks {

    /** Roughly ten thousand records; read as one list by the `finished` report, so it is held in memory. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ObjectMapper mapper;
    private final StateService stateService;
    private final Notifications notifications;
    private final MasterReview reviews;
    private final Path file;

    public FinishedTasks(ObjectMapper mapper, StateService stateService, Notifications notifications,
                         OrchestratorPaths paths, MasterReview reviews) {
        this.mapper = mapper;
        this.stateService = stateService;
        this.notifications = notifications;
        this.reviews = reviews;
        this.file = paths.stateFile().resolveSibling("finished.jsonl");
    }

    /**
     * Called while the task still exists: after retirement there is nothing left to record. The reviewer's
     * verdict is read HERE, from a worktree about to go — the kept artifacts are for a human and nothing
     * parses them, so a verdict nobody put in this record could never be compared with what happened next.
     */
    public void record(String taskId) {
        stateService.task(taskId).ifPresent(task -> append(FinishedTask.of(taskId, task,
                System.currentTimeMillis(), reviews.of(task).map(MasterReview.Verdict::said).orElse(null))));
    }

    /** Newest first. A line written by an older jagt that no longer parses is skipped, never fatal. */
    public List<FinishedTask> all() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.reversed().stream().filter(line -> !line.isBlank()).map(this::parse)
                    .filter(java.util.Objects::nonNull).toList();
        } catch (IOException e) {
            log.atWarn().setMessage("finished log unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", e.toString()).log();
            return List.of();
        }
    }

    private FinishedTask parse(String line) {
        try {
            return mapper.readValue(line, FinishedTask.class);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("finished entry unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", e.toString()).log();
            return null;
        }
    }

    private void append(FinishedTask finished) {
        try {
            Files.writeString(file, mapper.writeValueAsString(finished) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (Files.size(file) > MAX_BYTES) {
                notifications.send(Notification.housekeeping("finished.jsonl has passed "
                        + MAX_BYTES / (1024 * 1024) + "MB",
                        "still appending — archive it, and the `finished` report gets fast again"));
            }
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("finished entry not recorded").addKeyValue("task", finished.id())
                    .addKeyValue("cause", e.toString()).log();
        }
    }
}
