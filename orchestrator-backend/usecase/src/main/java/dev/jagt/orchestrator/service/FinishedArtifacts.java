package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
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

/**
 * The documents a finished task leaves behind, copied beside `state.json` before its worktree goes: one
 * directory per task, named so the listing is chronological and says which task it was.
 *
 * <p>Never read back — a record for a human's final review. That is what makes a format change free: old
 * directories stay as written and nothing parses them, so there is no version field and no migration. The day
 * code reads one this changes, and a stamp comes before the first reader.
 *
 * <p>Best-effort like the finished log: losing a copy must not refuse a `done`.
 */
@Service
@Slf4j
public class FinishedArtifacts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StateService stateService;
    private final Path store;

    public FinishedArtifacts(StateService stateService, OrchestratorPaths paths) {
        this.stateService = stateService;
        this.store = paths.stateFile().resolveSibling("artifacts");
    }

    /** Called while the worktree still exists: after retirement there is nothing left to copy. */
    public void keep(String taskId) {
        stateService.task(taskId).ifPresent(task -> copy(taskId, Path.of(task.worktreePath())));
    }

    private void copy(String taskId, Path worktree) {
        Path into = store.resolve(LocalDate.now().format(DAY) + "-" + TaskName.slug(taskId));
        try {
            for (String name : Artifact.fileNames()) {
                Path artifact = worktree.resolve(name);
                if (Files.isRegularFile(artifact)) {
                    Files.createDirectories(into);
                    Files.copy(artifact, into.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("finished artifacts not kept").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString()).log();
        }
    }
}
