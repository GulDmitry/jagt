package dev.jagt.orchestrator.adapter.replay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** A run's recording, and whether replaying it has already left what it recorded. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@Slf4j
public class Recording {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final List<RecordingLine> lines;
    private final AtomicBoolean diverged = new AtomicBoolean();

    public Recording(@Value("${orchestrator.replay.file}") String file) {
        this.lines = read(Path.of(file));
    }

    public static List<RecordingLine> read(Path file) {
        try {
            return Files.readAllLines(file).stream().filter(line -> !line.isBlank())
                    .map(line -> JSON.readValue(line, RecordingLine.class)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the recording " + file, e);
        }
    }

    public static void write(Path file, List<RecordingLine> lines) {
        try {
            Files.writeString(file, lines.stream().map(line -> JSON.writerFor(RecordingLine.class).writeValueAsString(line))
                    .collect(Collectors.joining("\n", "", "\n")));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the recording " + file, e);
        }
    }

    public List<RecordingLine> lines() {
        return lines;
    }

    public boolean diverged() {
        return diverged.get();
    }

    /** The first divergence ends the replay; a later one only follows from it. */
    public void diverge(String task, String step, String cause) {
        if (diverged.compareAndSet(false, true)) {
            log.atError().setMessage("replay diverged").addKeyValue("task", task).addKeyValue("step", step)
                    .addKeyValue("cause", cause).log();
        }
    }
}
