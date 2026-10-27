package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.AtomicFiles;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    static Optional<byte[]> bytes(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException unreadable) {
            log.atWarn().setMessage("record unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString()).log();
            return Optional.empty();
        }
    }

    static <T> Optional<T> parse(Path file, byte[] bytes, Class<T> type) {
        try {
            return Optional.of(MAPPER.readValue(bytes, type));
        } catch (JacksonException unreadable) {
            log.atWarn().setMessage("record unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.getOriginalMessage()).log();
            return Optional.empty();
        }
    }

    static <T> Optional<T> read(Path file, Class<T> type) {
        return bytes(file).flatMap(read -> parse(file, read, type));
    }

    /** SHA-256 in hex. */
    static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** Empty where the value could not be serialised. */
    static Optional<byte[]> serialise(Object value) {
        try {
            return Optional.of(MAPPER.writeValueAsBytes(value));
        } catch (JacksonException unwritable) {
            log.atWarn().setMessage("record unwritten").addKeyValue("cause", unwritable.toString()).log();
            return Optional.empty();
        }
    }

    static boolean write(Path file, Object value) {
        return serialise(value).map(bytes -> write(file, bytes)).orElse(false);
    }

    /** Whole or not at all, so a reader never meets half a record. */
    static boolean write(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            AtomicFiles.write(file, bytes);
            return true;
        } catch (IOException unwritable) {
            log.atWarn().setMessage("record unwritten").addKeyValue("file", file)
                    .addKeyValue("cause", unwritable.toString()).log();
            return false;
        }
    }
}
