package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What the human pressed, one line per press beside `state.json`. A press names a control and the status of the
 * task it was on, never a task, a ticket or typed text. Past {@link #MAX_BYTES} the file rolls
 * into one `.1`, replacing the last.
 */
@Service
@Slf4j
public class PressLog {

    static final long MAX_BYTES = 1024L * 1024;
    static final int MAX_BATCH = 50;

    private static final Pattern CONTROL = Pattern.compile("[a-z0-9][a-z0-9:#._-]{0,47}");
    private static final Pattern STATUS = Pattern.compile("[A-Z_]{1,24}");
    private static final long DAY = 24L * 60 * 60 * 1000;

    public record Interaction(long at, String control, @Nullable String status) {
    }

    private final ObjectMapper mapper;
    private final Path file;
    private final boolean enabled;

    public PressLog(ObjectMapper mapper, OrchestratorPaths paths,
                    @Value("${orchestrator.interaction-log:true}") boolean enabled) {
        this.mapper = mapper;
        this.file = paths.stateFile().resolveSibling("interactions.jsonl");
        this.enabled = enabled;
    }

    /** Refuses the whole batch on one bad entry, naming it; a refused or switched-off batch writes nothing. */
    public void record(List<Interaction> batch, long now) {
        if (batch.size() > MAX_BATCH) {
            throw new IllegalArgumentException("A batch holds at most " + MAX_BATCH + " presses, not " + batch.size());
        }
        batch.forEach(press -> check(press, now));
        if (!enabled || batch.isEmpty()) {
            return;
        }
        String lines = batch.stream().map(mapper::writeValueAsString).collect(Collectors.joining("\n", "", "\n"));
        append(lines);
    }

    private static void check(@Nullable Interaction press, long now) {
        if (press == null) {
            throw new IllegalArgumentException("A batch holds presses, not an empty entry");
        }
        if (press.control() == null || !CONTROL.matcher(press.control()).matches()) {
            throw new IllegalArgumentException("A press names a control in lower case, not \"" + press.control() + "\"");
        }
        if (press.status() != null && !STATUS.matcher(press.status()).matches()) {
            throw new IllegalArgumentException("A press carries a task status, not \"" + press.status() + "\"");
        }
        if (Math.abs(now - press.at()) > DAY) {
            throw new IllegalArgumentException("A press is dated within a day of now, not " + press.at());
        }
    }

    private synchronized void append(String lines) {
        try {
            Files.writeString(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (Files.size(file) > MAX_BYTES) {
                Files.move(file, file.resolveSibling(file.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.atWarn().setMessage("presses not recorded").addKeyValue("file", file)
                    .addKeyValue("cause", e.toString()).log();
        }
    }
}
