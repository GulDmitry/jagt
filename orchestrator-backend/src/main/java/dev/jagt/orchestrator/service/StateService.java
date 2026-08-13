package dev.jagt.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.model.TaskState;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * SSOT for all active tasks. Every mutation rewrites state.json atomically
 * (temp file + Files.move with ATOMIC_MOVE) so a crash never leaves a torn file.
 */
@Service
public class StateService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StateFile(Map<String, TaskState> tasks) {

        public StateFile {
            tasks = tasks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tasks);
        }
    }

    private final ObjectMapper mapper;
    private final Path stateFile;
    private final Object lock = new Object();

    public StateService(ObjectMapper mapper, OrchestratorPaths paths) {
        // Tolerate state.json files written before a new primitive field existed: a missing/null primitive
        // must default to 0/false, not blow up the whole load (which would strand every task). Without this,
        // adding a `long`/`boolean` to TaskState makes older state.json unreadable ("Cannot map null into long").
        this.mapper = mapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        this.stateFile = paths.stateFile();
    }

    public StateFile read() {
        synchronized (lock) {
            return readUnlocked();
        }
    }

    public Map<String, TaskState> tasks() {
        return read().tasks();
    }

    public Optional<TaskState> task(String taskId) {
        return Optional.ofNullable(tasks().get(taskId));
    }

    /**
     * The task id behind an id OR its short alias (p1, s2, …) — aliases are state, so resolving them
     * belongs here and every caller that accepts human input goes through this one place. An unknown
     * value is returned unchanged, so callers still produce their own "not found" error.
     */
    public String canonicalTaskId(String idOrAlias) {
        if (idOrAlias == null || task(idOrAlias).isPresent()) {
            return idOrAlias;
        }
        return tasks().entrySet().stream()
                .filter(e -> idOrAlias.equalsIgnoreCase(e.getValue().alias()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(idOrAlias);
    }

    public void putTask(String taskId, TaskState state) {
        mutate(file -> {
            file.tasks().put(taskId, state);
            return file;
        });
    }

    public boolean updateTask(String taskId, UnaryOperator<TaskState> update) {
        AtomicBoolean found = new AtomicBoolean();
        mutate(file -> {
            TaskState current = file.tasks().get(taskId);
            if (current != null) {
                file.tasks().put(taskId, update.apply(current));
                found.set(true);
            }
            return file;
        });
        return found.get();
    }

    public boolean removeTask(String taskId) {
        AtomicBoolean found = new AtomicBoolean();
        mutate(file -> {
            found.set(file.tasks().remove(taskId) != null);
            return file;
        });
        return found.get();
    }

    /** Resolves the calling agent's task from the X-Working-Directory header value. */
    public Optional<Map.Entry<String, TaskState>> findByWorktree(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return Optional.empty();
        }
        Path callerPath = canonical(Path.of(cwd));
        return tasks().entrySet().stream()
                .filter(e -> {
                    Path worktree = canonical(Path.of(e.getValue().worktreePath()));
                    return callerPath.equals(worktree) || callerPath.startsWith(worktree);
                })
                .findFirst();
    }

    /**
     * Node's process.cwd() reports the physical path (symlinks resolved, e.g.
     * /private/tmp), while configured paths may be logical (/tmp) — compare
     * physical to physical.
     */
    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** The single JSON rendering of orchestrator state, same shape as state.json on disk. */
    public String prettyJson() {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(read());
    }

    private void mutate(UnaryOperator<StateFile> mutation) {
        synchronized (lock) {
            writeUnlocked(mutation.apply(readUnlocked()));
        }
    }

    private StateFile readUnlocked() {
        try {
            if (!Files.exists(stateFile)) {
                return new StateFile(null);
            }
            return mapper.readValue(Files.readString(stateFile), StateFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read state file " + stateFile, e);
        }
    }

    private void writeUnlocked(StateFile state) {
        try {
            Path temp = Files.createTempFile(stateFile.getParent(), "state", ".json.tmp");
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
            Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write state file " + stateFile, e);
        }
    }
}
