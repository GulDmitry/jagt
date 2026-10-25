package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.AtomicFiles;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** A record jagt keeps for itself under a worktree's `.jagt/`, out of git. */
@Slf4j
final class JagtJson {

    private static final JsonMapper MAPPER = new JsonMapper();

    private JagtJson() {
    }

    static Path in(String worktree, String name) {
        return Path.of(worktree).resolve(".jagt").resolve(name);
    }

    /** Empty where none was written, or it could not be read. */
    static <T> Optional<T> read(Path file, Class<T> type) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), type));
        } catch (JacksonException unreadable) {
            log.atWarn().setMessage("record unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.getOriginalMessage()).log();
            return Optional.empty();
        }
    }

    /** Whole or not at all, so a reader never meets half a record. */
    static boolean write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            AtomicFiles.write(file, MAPPER.writeValueAsBytes(value));
            return true;
        } catch (IOException | JacksonException unwritable) {
            log.atWarn().setMessage("record unwritten").addKeyValue("file", file)
                    .addKeyValue("cause", unwritable.toString()).log();
            return false;
        }
    }
}
