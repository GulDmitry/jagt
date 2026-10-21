package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** The documents of a task that sit in its worktree right now, read from disk on every call and kept nowhere. */
@Service
@RequiredArgsConstructor
public class TaskArtifacts {

    static final long MAX_BYTES = 1024 * 1024;

    /** The name is not one of {@link Artifact}. */
    public static class UnknownArtifact extends IllegalArgumentException {
        UnknownArtifact(String name) {
            super("'" + name + "' is not a document of a task");
        }
    }

    /** The name is a document of a task, and the worktree holds no such file. */
    public static class ArtifactAbsent extends IllegalStateException {
        ArtifactAbsent(String name) {
            super(name + " is not in the worktree");
        }
    }

    /** The file is there and could not be read, or is too large to. */
    public static class ArtifactUnreadable extends IllegalStateException {
        ArtifactUnreadable(String message, Throwable cause) {
            super(message, cause);
        }

        ArtifactUnreadable(String message) {
            super(message);
        }
    }

    private final StateService state;

    public List<String> present(String taskId) {
        Path worktree = worktree(taskId);
        return Arrays.stream(Artifact.values())
                .map(Artifact::fileName)
                .filter(name -> Files.isRegularFile(worktree.resolve(name)))
                .toList();
    }

    public String read(String taskId, String name) {
        Path worktree = worktree(taskId);
        Artifact artifact = Arrays.stream(Artifact.values())
                .filter(one -> one.fileName().equals(name))
                .findFirst()
                .orElseThrow(() -> new UnknownArtifact(name));
        Path file = worktree.resolve(artifact.fileName());
        if (!Files.isRegularFile(file)) {
            throw new ArtifactAbsent(name);
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new ArtifactUnreadable(name + " is over " + MAX_BYTES / 1024 + " KiB");
            }
            return Files.readString(file);
        } catch (IOException e) {
            throw new ArtifactUnreadable("could not read " + name + ": " + e.getMessage(), e);
        }
    }

    private Path worktree(String taskId) {
        String id = state.canonicalTaskId(taskId);
        return state.task(id).map(TaskState::worktreePath)
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }
}
