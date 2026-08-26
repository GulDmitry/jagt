package dev.jagt.orchestrator.adapter.agent;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Claude Code's own log of a session: one file per session, in a directory named after the directory the
 * session runs in. The newest of them is the session still going.
 *
 * <p>That directory name is DERIVED rather than read anywhere — every character outside {@code [A-Za-z0-9-]}
 * becomes a dash. A name this does not reconstruct simply answers 0, so being wrong costs the caller a sign
 * and never a false one.
 */
@Slf4j
final class ClaudeTranscripts {

    private static final String SUFFIX = ".jsonl";

    private ClaudeTranscripts() {
    }

    /** Relocating the whole directory is supported, so a human who did must not silently lose the answer. */
    static Path projectsDir() {
        String configured = System.getenv("CLAUDE_CONFIG_DIR");
        return (configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".claude")
                : Path.of(configured)).resolve("projects");
    }

    static long lastEntryMillis(Path projectsDir, Path sessionDirectory) {
        Path dir = projectsDir.resolve(slug(sessionDirectory));
        // A session that has written nothing yet is not a failure, and neither is a name this did not
        // reconstruct: both are simply no sign. Anything else IS one, and answering 0 for it would put a broken
        // read and a quiet session under the same number.
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> logs = Files.list(dir)) {
            return logs.filter(log -> log.getFileName().toString().endsWith(SUFFIX))
                    .mapToLong(ClaudeTranscripts::modified)
                    .max()
                    .orElse(0);
        } catch (IOException e) {
            log.atWarn().setMessage("session record unreadable")
                    .addKeyValue("dir", dir)
                    .addKeyValue("cause", e.toString())
                    .log();
            return 0;
        }
    }

    private static long modified(Path log) {
        try {
            return Files.getLastModifiedTime(log).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** The PHYSICAL path is what the name is built from, so a worktree under a symlink still finds its logs. */
    static String slug(Path sessionDirectory) {
        return physical(sessionDirectory).toString().replaceAll("[^A-Za-z0-9-]", "-");
    }

    private static Path physical(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            return directory.toAbsolutePath().normalize();
        }
    }
}
