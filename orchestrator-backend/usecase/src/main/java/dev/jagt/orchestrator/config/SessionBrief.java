package dev.jagt.orchestrator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A section the human's copy lacks is read from the shipped file; one left empty says nothing. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionBrief {

    private static final String COPY = "session-brief.md";
    private static final String SHIPPED = COPY + ".dist";
    private static final String HEADING = "## ";

    private final OrchestratorPaths paths;

    public Optional<String> section(String step) {
        return read(COPY).flatMap(text -> section(text, step)).or(() -> read(SHIPPED).flatMap(text -> section(text, step)));
    }

    private Optional<String> read(String name) {
        Path file = paths.root().resolve(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException unreadable) {
            log.atError().setMessage("session brief unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }

    private static Optional<String> section(String text, String step) {
        List<String> lines = text.lines().toList();
        int start = lines.indexOf(HEADING + step);
        if (start < 0) {
            return Optional.empty();
        }
        int end = start + 1;
        while (end < lines.size() && !lines.get(end).startsWith(HEADING)) {
            end++;
        }
        return Optional.of(String.join("\n", lines.subList(start + 1, end)).strip());
    }
}
