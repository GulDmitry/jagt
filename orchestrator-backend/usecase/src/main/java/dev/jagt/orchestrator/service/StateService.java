package dev.jagt.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * SSOT for all active tasks. Every mutation rewrites the file atomically, so a crash never leaves a torn file.
 *
 * <p>Atomicity protects against a TORN file, not against a bad one: a hand edit, a botched migration or a
 * serialization bug can leave valid-but-wrong or unparseable JSON, and this one file is the only record of
 * what jagt is doing. So every write keeps the previous version next to it as {@code state.json.bak}, and a
 * read that cannot parse the primary recovers from that backup instead of losing every task. It NEVER starts
 * empty over an existing file — that would destroy the human's data on the very next write.
 */
@Service
@Slf4j
public class StateService implements dev.jagt.orchestrator.port.TaskStore {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StateFile(Map<String, TaskState> tasks) {

        public StateFile {
            tasks = tasks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tasks);
        }
    }

    private final ObjectMapper mapper;
    private final Path stateFile;
    private final Path backupFile;
    private final Path corruptFile;
    private final Object lock = new Object();
    private final List<Consumer<StateFile>> changeListeners = new CopyOnWriteArrayList<>();
    /**
     * The last parse, keyed by the file version it came from. Reading is the hot path — a single dashboard
     * render asks a dozen times — and re-parsing per accessor also means one decision can straddle two
     * versions of the file. Handed out as a COPY, always: callers mutate the task map in place.
     */
    private StateFile cached;
    private FileVersion cachedVersion;

    private record FileVersion(FileTime modified, long size) {
    }

    public StateService(ObjectMapper mapper, OrchestratorPaths paths) {
        // A missing primitive defaults to 0/false instead of failing the whole load: adding a `long` to
        // TaskState would otherwise make every older state.json unreadable.
        this.mapper = mapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        this.stateFile = paths.stateFile();
        this.backupFile = sibling(paths.stateFile(), ".bak");
        this.corruptFile = sibling(paths.stateFile(), ".corrupt");
    }

    private static Path sibling(Path file, String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    /**
     * Registers a listener for "state changed", called with the state as it was just written.
     *
     * <p>Guarantees, because both consumers depend on them: it fires AFTER the file is on disk (so a listener
     * that re-reads sees the same thing), it fires OUTSIDE the write lock (a slow listener must not block the
     * agents' MCP calls), it does NOT fire when a mutation changed nothing, and one listener throwing neither
     * fails the mutation nor stops the others. Coalescing rapid changes is the LISTENER's business — this
     * publisher reports every change it makes.
     */
    public void onChange(Consumer<StateFile> listener) {
        changeListeners.add(listener);
    }

    public StateFile read() {
        synchronized (lock) {
            return readUnlocked();
        }
    }

    public Map<String, TaskState> tasks() {
        return read().tasks();
    }

    @Override
    public Optional<TaskState> task(String taskId) {
        return Optional.ofNullable(tasks().get(taskId));
    }

    /**
     * The task id behind an id OR its short alias. An unknown value is returned UNCHANGED, so callers still
     * produce their own "not found" error.
     */
    @Override
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
            file.tasks().put(taskId, stamped(file.tasks().get(taskId), state));
            return file;
        });
    }

    @Override
    public boolean updateTask(String taskId, UnaryOperator<TaskState> update) {
        AtomicBoolean found = new AtomicBoolean();
        mutate(file -> {
            TaskState current = file.tasks().get(taskId);
            if (current != null) {
                file.tasks().put(taskId, stamped(current, update.apply(current)));
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

    /**
     * The task a caller's working directory belongs to. ANY of the task's repositories answers, not just the
     * first: one piece of work can span several, and a session started in any of them belongs to the same task —
     * so a multi-repo task is ONE caller, not several.
     */
    public Optional<Map.Entry<String, TaskState>> findByWorktree(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return Optional.empty();
        }
        Path callerPath = canonical(Path.of(cwd));
        return tasks().entrySet().stream()
                .filter(e -> e.getValue().repos().stream().anyMatch(repo -> holds(repo, callerPath)))
                .findFirst();
    }

    private static boolean holds(TaskRepo repo, Path callerPath) {
        if (repo.worktreePath() == null || repo.worktreePath().isBlank()) {
            return false;
        }
        Path worktree = canonical(Path.of(repo.worktreePath()));
        return callerPath.equals(worktree) || callerPath.startsWith(worktree);
    }

    /**
     * A caller may report the physical path (symlinks resolved, e.g. /private/tmp) where configured paths are
     * logical (/tmp) — compare physical to physical.
     */
    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    public String prettyJson() {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(read());
    }

    /**
     * Names the asker on a step that was just taken. Every persisted change funnels through here, which is the
     * only layer that sees both the step and {@link OriginContext} — the code that builds a transition is too
     * far from the entry point to know who triggered it.
     */
    private static TaskState stamped(TaskState before, TaskState after) {
        ActionOrigin origin = OriginContext.current();
        if (origin == null || after.history().isEmpty() || !tookAStep(before, after)) {
            return after;
        }
        return after.withLastChangeOrigin(origin);
    }

    private static boolean tookAStep(TaskState before, TaskState after) {
        if (before == null) {
            return true;
        }
        if (before.history().isEmpty()) {
            // A task written before history existed has its current status reconstructed on the next write.
            // That entry is a reconstruction of something that happened long ago, not a step anyone just took,
            // so a keep-alive must not sign it — least of all with a status a human reached days earlier.
            return after.history().size() > 1;
        }
        return !before.history().getLast().equals(after.history().getLast());
    }

    private void mutate(UnaryOperator<StateFile> mutation) {
        StateFile written;
        synchronized (lock) {
            StateFile before = readUnlocked();
            // The map is mutated in place by the callers' operators, so snapshot BEFORE applying it —
            // otherwise "did anything change?" compares the new state with itself.
            StateFile unchanged = new StateFile(before.tasks());
            written = mutation.apply(before);
            if (unchanged.equals(written)) {
                return;
            }
            writeUnlocked(written);
        }
        publish(written);                      // outside the lock: a listener must never block a tool call
    }

    private void publish(StateFile written) {
        StateFile snapshot = new StateFile(written.tasks());   // listeners cannot disturb each other
        for (Consumer<StateFile> listener : changeListeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException e) {
                log.atWarn().setMessage("state listener failed")
                        .addKeyValue("cause", e.toString())
                        .addKeyValue("effect", "the write itself is done")
                        .log();
            }
        }
    }

    private StateFile readUnlocked() {
        if (!Files.exists(stateFile)) {
            cache(null, null);
            return new StateFile(null);
        }
        FileVersion version = versionOnDisk();
        if (cached != null && cachedVersion != null && cachedVersion.equals(version)) {
            return new StateFile(cached.tasks());
        }
        StateFile parsed;
        try {
            parsed = parse(stateFile);
        } catch (RuntimeException | IOException primaryFailure) {
            cache(null, null);
            return putBack(recoverFromBackup(primaryFailure));
        }
        cache(parsed, version);
        return parsed;
    }

    /**
     * A recovery has just moved the unreadable primary aside, so there is no state file at all — and the very
     * next read would answer "no tasks" over a backup that still holds them, which is the one outcome this
     * class exists to prevent. Best effort: a recovery that cannot be persisted is still worth returning.
     */
    private StateFile putBack(StateFile recovered) {
        if (Files.exists(stateFile)) {
            // The unreadable file could not be moved aside; writing now would copy it over the backup first.
            return recovered;
        }
        try {
            writeUnlocked(recovered);
        } catch (RuntimeException e) {
            log.atError().setMessage("state writeback failed")
                    .addKeyValue("file", stateFile)
                    .addKeyValue("tasks", recovered.tasks().size())
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
        return recovered;
    }

    private void cache(StateFile state, FileVersion version) {
        cached = state == null || version == null ? null : new StateFile(state.tasks());
        cachedVersion = version;
    }

    /** Null when the attributes cannot be read, which simply keeps every read a re-parse. */
    private FileVersion versionOnDisk() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(stateFile, BasicFileAttributes.class);
            return new FileVersion(attributes.lastModifiedTime(), attributes.size());
        } catch (IOException e) {
            return null;
        }
    }

    private StateFile parse(Path file) throws IOException {
        return mapper.readValue(Files.readString(file), StateFile.class);
    }

    /**
     * The primary is unreadable. Fall back to the backup and keep the bad file for inspection — but only when
     * the backup actually parses: with nothing to fall back to, FAILING is the safe outcome. Starting empty
     * would look like "no tasks" and the next write would overwrite whatever the human might still salvage.
     */
    private StateFile recoverFromBackup(Exception primaryFailure) {
        if (Files.exists(backupFile)) {
            try {
                StateFile recovered = parse(backupFile);
                log.atError().setMessage("state file unreadable")
                        .addKeyValue("file", stateFile)
                        .addKeyValue("cause", primaryFailure.getMessage())
                        .addKeyValue("tasks", recovered.tasks().size())
                        .addKeyValue("from", backupFile)
                        .addKeyValue("kept", corruptFile)
                        .log();
                setAsideCorruptFile();
                return recovered;
            } catch (RuntimeException | IOException backupFailure) {
                log.atError().setMessage("state backup unreadable")
                        .addKeyValue("file", backupFile)
                        .addKeyValue("cause", backupFailure.getMessage())
                        .log();
            }
        }
        throw new UncheckedIOException("Cannot read state file " + stateFile + " and no usable backup at "
                + backupFile + " — fix or remove the file; jagt will not start with an empty task list over"
                + " an existing state file", asIoException(primaryFailure));
    }

    private void setAsideCorruptFile() {
        try {
            Files.move(stateFile, corruptFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.atWarn().setMessage("state file move aside failed")
                    .addKeyValue("from", stateFile)
                    .addKeyValue("to", corruptFile)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
    }

    private static IOException asIoException(Exception e) {
        return e instanceof IOException io ? io : new IOException(e);
    }

    private void writeUnlocked(StateFile state) {
        backUpCurrentVersion();
        try {
            Path temp = Files.createTempFile(stateFile.getParent(), "state", ".json.tmp");
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
            Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            cache(null, null);
            throw new UncheckedIOException("Cannot write state file " + stateFile, e);
        }
        // Own writes never rely on the version key: two of them within one filesystem timestamp tick are
        // indistinguishable by it, and this is the only writer that can rewrite the file that fast.
        cache(state, versionOnDisk());
    }

    /** Best-effort: a failed backup must not stop the write — the live file matters more than its copy. */
    private void backUpCurrentVersion() {
        if (!Files.exists(stateFile)) {
            return;
        }
        try {
            Files.copy(stateFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.atWarn().setMessage("state backup failed")
                    .addKeyValue("from", stateFile)
                    .addKeyValue("to", backupFile)
                    .addKeyValue("cause", e.getMessage())
                    .log();
        }
    }
}
