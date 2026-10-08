package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The documents a finished task leaves behind, copied beside `state.json` before its worktree goes: one
 * directory per task, named so the listing is chronological and says which task it was.
 *
 * <p>Never read back — a record for a human's final review. That is what makes a format change free: old
 * directories stay as written and nothing parses them, so there is no version field and no migration. The day
 * code reads one this changes, and a stamp comes before the first reader.
 *
 * <p>Best-effort like the finished log: losing a copy must not refuse a `done`. Nothing here is unbounded
 * either, and the bound REPORTS rather than refuses — dropping the record of a finished task is worse than
 * holding too many, so past a limit the copy still happens and a human is told to prune.
 */
@Service
@Slf4j
public class FinishedArtifacts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** One per finished task, so this is the count of tasks whose documents are still on this disk. */
    static final int MAX_DIRECTORIES = 1_000;

    /** A document this big is a runaway write, not a plan somebody will read. */
    static final long MAX_ARTIFACT_BYTES = 10L * 1024 * 1024;

    private final StateService stateService;
    private final Notifications notifications;
    private final Path store;

    public FinishedArtifacts(StateService stateService, Notifications notifications,
                             OrchestratorPaths paths) {
        this.stateService = stateService;
        this.notifications = notifications;
        this.store = paths.stateFile().resolveSibling("artifacts");
    }

    /** Called while the worktree still exists: after retirement there is nothing left to copy. */
    public void keep(String taskId) {
        stateService.task(taskId).ifPresent(task -> copy(taskId, Path.of(task.worktreePath())));
    }

    private void reportIfOversized(String taskId, String name, long bytes) {
        if (bytes > MAX_ARTIFACT_BYTES) {
            notifications.send(Notification.housekeeping(name + " of " + taskId + " is "
                    + bytes / (1024 * 1024) + "MB",
                    "kept anyway — a document past " + MAX_ARTIFACT_BYTES / (1024 * 1024)
                            + "MB is a runaway write worth looking at"));
        }
    }

    /** Counted after the copy, never before it: the report is a reminder, never a reason to drop a record. */
    private void reportIfFull() throws IOException {
        if (!Files.isDirectory(store)) {
            return;
        }
        long kept;
        try (Stream<Path> stored = Files.list(store)) {
            kept = stored.count();
        }
        pruneAsked(kept).ifPresent(notifications::send);
    }

    static Optional<Notification> pruneAsked(long kept) {
        return kept < MAX_DIRECTORIES ? Optional.empty() : Optional.of(Notification.housekeeping(
                kept + " finished tasks kept on disk",
                "artifacts/ has passed " + MAX_DIRECTORIES + " directories — delete the ones you have read"));
    }

    private void copy(String taskId, Path worktree) {
        Path into = store.resolve(LocalDate.now().format(DAY) + "-" + TaskName.slug(taskId));
        try {
            for (String name : Artifact.fileNames()) {
                Path artifact = worktree.resolve(name);
                if (Files.isRegularFile(artifact)) {
                    Files.createDirectories(into);
                    Files.copy(artifact, into.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    reportIfOversized(taskId, name, Files.size(artifact));
                }
            }
            reportIfFull();
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("finished artifacts not kept").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString()).log();
        }
    }
}
