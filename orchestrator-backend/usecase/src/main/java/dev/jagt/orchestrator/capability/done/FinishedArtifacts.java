package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * One snapshot per `done`, copied beside `state.json` before its worktree goes: the task's documents and its
 * `finished.json`, in a directory named to the second so a reopened task keeps every snapshot. Past the byte
 * budget the oldest entries go first.
 *
 * <p>Never read back — a record for a human's final review, so old directories stay as written and nothing
 * parses them. The day code reads one this changes, and a stamp comes before the first reader.
 *
 * <p>Best-effort like the finished log: losing a copy must not refuse a `done`.
 */
@Service
@Slf4j
public class FinishedArtifacts {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");
    private static final String LEGACY_ZIP_PREFIX = "archive-";

    /** A document this big is a runaway write, not a plan somebody will read. */
    static final long MAX_ARTIFACT_BYTES = 10L * 1024 * 1024;

    private final StateService stateService;
    private final Path store;
    private final Clock clock;
    private final ConfigService config;
    private final ObjectMapper mapper;

    public FinishedArtifacts(StateService stateService, OrchestratorPaths paths, Clock clock,
                             ConfigService config, ObjectMapper mapper) {
        this.stateService = stateService;
        this.clock = clock;
        this.config = config;
        this.mapper = mapper;
        this.store = paths.stateFile().resolveSibling("archive");
    }

    /** Called while the worktree still exists: after retirement there is nothing left to copy. */
    public void keep(FinishedTask finished) {
        stateService.task(finished.id()).ifPresent(task -> copy(finished, Path.of(task.worktreePath())));
    }

    private void copy(FinishedTask finished, Path worktree) {
        Path into = store.resolve(LocalDateTime.now(clock).format(STAMP) + "-" + TaskName.slug(finished.id()));
        try {
            Files.createDirectories(into);
            for (String name : Artifact.fileNames()) {
                copyArtifact(worktree.resolve(name), into, name);
            }
            Files.writeString(into.resolve("finished.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finished), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("finished artifacts not kept").addKeyValue("task", finished.id())
                    .addKeyValue("cause", e.toString()).log();
        }
        trimToBudget(into);
    }

    private void copyArtifact(Path artifact, Path into, String name) throws IOException {
        if (!Files.isRegularFile(artifact)) {
            return;
        }
        long bytes = Files.size(artifact);
        if (bytes > MAX_ARTIFACT_BYTES) {
            Files.writeString(into.resolve(name + ".not-kept"), "%s was %d bytes, past the %d kept"
                    .formatted(name, bytes, MAX_ARTIFACT_BYTES), StandardCharsets.UTF_8);
        } else {
            Files.copy(artifact, into.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void trimToBudget(Path newest) {
        try {
            long budget = config.load().archive().maxBytesOrDefault();
            List<Path> entries = entriesOldestFirst();
            long total = 0;
            for (Path entry : entries) {
                total += bytes(entry);
            }
            for (Path entry : entries) {
                if (total <= budget || entry.equals(newest)) {
                    break;
                }
                total -= bytes(entry);
                deleteTree(entry);
                log.atInfo().setMessage("finished artifacts dropped").addKeyValue("entry", entry.getFileName())
                        .addKeyValue("budget", budget).log();
            }
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("finished artifacts not trimmed").addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "the store stays past its budget until the next done").log();
        }
    }

    private List<Path> entriesOldestFirst() throws IOException {
        try (Stream<Path> stored = Files.list(store)) {
            return stored.sorted(Comparator.comparing((Path entry) -> !isLegacyZip(entry))
                    .thenComparing(Path::getFileName)).toList();
        }
    }

    private static boolean isLegacyZip(Path entry) {
        return entry.getFileName().toString().startsWith(LEGACY_ZIP_PREFIX);
    }

    private static long bytes(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            long total = 0;
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                total += Files.size(file);
            }
            return total;
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
