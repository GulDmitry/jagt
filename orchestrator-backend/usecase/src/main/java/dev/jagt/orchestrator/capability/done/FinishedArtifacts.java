package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");
    private static final String ARCHIVE_PREFIX = "archive-";

    /** One per finished task; past it the older half is zipped into one file. */
    static final int MAX_DIRECTORIES = 1_000;

    /** A document this big is a runaway write, not a plan somebody will read. */
    static final long MAX_ARTIFACT_BYTES = 10L * 1024 * 1024;

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
        Path into = store.resolve(LocalDate.now(ZoneOffset.UTC).format(DAY) + "-" + TaskName.slug(taskId));
        try {
            for (String name : Artifact.fileNames()) {
                Path artifact = worktree.resolve(name);
                if (Files.isRegularFile(artifact)) {
                    Files.createDirectories(into);
                    long bytes = Files.size(artifact);
                    if (bytes > MAX_ARTIFACT_BYTES) {
                        Files.writeString(into.resolve(name + ".not-kept"), "%s was %d bytes, past the %d kept"
                                .formatted(name, bytes, MAX_ARTIFACT_BYTES), StandardCharsets.UTF_8);
                    } else {
                        Files.copy(artifact, into.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            packOlderHalfWhenFull();
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("finished artifacts not kept").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString()).log();
        }
    }

    private void packOlderHalfWhenFull() throws IOException {
        if (!Files.isDirectory(store)) {
            return;
        }
        List<Path> tasks;
        try (Stream<Path> stored = Files.list(store)) {
            tasks = stored.filter(Files::isDirectory).sorted().toList();
        }
        if (tasks.size() < MAX_DIRECTORIES) {
            return;
        }
        List<Path> older = tasks.subList(0, tasks.size() / 2);
        Path archive = store.resolve(ARCHIVE_PREFIX + LocalDateTime.now(ZoneOffset.UTC).format(STAMP) + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW))) {
            for (Path task : older) {
                zipInto(zip, task);
            }
        }
        for (Path task : older) {
            deleteTree(task);
        }
        log.atInfo().setMessage("finished artifacts packed").addKeyValue("archive", archive)
                .addKeyValue("packed", older.size()).log();
    }

    private void zipInto(ZipOutputStream zip, Path task) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(task)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            zip.putNextEntry(new ZipEntry(store.relativize(file).toString()));
            Files.copy(file, zip);
            zip.closeEntry();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
