package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.port.LineDiff;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The plan of a task as it was when last approved, kept in jagt's own root where no session edits it. */
@Service
@RequiredArgsConstructor
public class PlanBaseline {

    private final OrchestratorPaths paths;
    private final TaskArtifacts artifacts;
    private final LineDiff diff;

    /** The plan in the worktree now becomes the baseline; a worktree with no plan leaves none. */
    public void keep(String taskId) {
        Path file = file(taskId);
        try {
            Files.createDirectories(file.getParent());
            try {
                Files.writeString(file, artifacts.read(taskId, Artifact.PLAN.fileName()));
            } catch (TaskArtifacts.ArtifactAbsent absent) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not keep the approved plan of " + taskId, e);
        }
    }

    /** What `plan` changed since the last approval; nothing while no plan was approved. */
    public LineChange since(String taskId, String plan) {
        Path file = file(taskId);
        if (!Files.isRegularFile(file)) {
            return LineChange.none();
        }
        try {
            return diff.between(Files.readString(file), plan);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the approved plan of " + taskId, e);
        }
    }

    private Path file(String taskId) {
        return paths.planBaselines().resolve(TaskName.slug(taskId) + ".md");
    }
}
