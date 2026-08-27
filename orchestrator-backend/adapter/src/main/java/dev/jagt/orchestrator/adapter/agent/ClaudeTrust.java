package dev.jagt.orchestrator.adapter.agent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A directory Claude has never run in is untrusted, and it discards that directory's whole permission file
 * until a human accepts the trust dialog in it — so an unattended session stops at prompts nobody in the tmux
 * window answers. Every worktree is such a directory. Recording the acceptance is the non-interactive way
 * Claude itself names.
 *
 * <p>Claude writes this file too, under a lock this cannot take, so the read and the replace are kept adjacent
 * and anything unexpected in it costs the flag rather than the file.
 */
@Slf4j
final class ClaudeTrust {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final String PROJECTS = "projects";
    private static final String TRUSTED = "hasTrustDialogAccepted";
    private static final String UNTRUSTED = "worktree left untrusted";

    private ClaudeTrust() {
    }

    /** Relocating the whole directory is supported, so a human who did must not silently lose the answer. */
    static Path configFile() {
        String configured = System.getenv("CLAUDE_CONFIG_DIR");
        return (configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"))
                : Path.of(configured)).resolve(".claude.json");
    }

    static void accept(Path configFile, Path worktree) {
        Path staged = null;
        try {
            String current = Files.exists(configFile) ? Files.readString(configFile) : "{}";
            String updated = accepted(current, resolved(worktree));
            if (updated == null) {
                return;
            }
            staged = Files.createTempFile(configFile.getParent(), ".claude", ".json");
            Files.writeString(staged, updated);
            Files.move(staged, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JacksonException e) {
            log.atWarn().setMessage(UNTRUSTED)
                    .addKeyValue("file", configFile)
                    .addKeyValue("worktree", worktree)
                    .addKeyValue("cause", e.toString())
                    .log();
        } finally {
            discard(staged);
        }
    }

    /** Null rather than the unchanged text, so a file nothing was added to is never rewritten. */
    static String accepted(String config, String worktree) {
        JsonNode parsed = JSON.readTree(config.isBlank() ? "{}" : config);
        if (!parsed.isObject()) {
            log.atWarn().setMessage(UNTRUSTED)
                    .addKeyValue("worktree", worktree)
                    .addKeyValue("cause", "config holds " + parsed.getNodeType())
                    .log();
            return null;
        }
        ObjectNode root = (ObjectNode) parsed;
        ObjectNode projects = root.path(PROJECTS).isObject()
                ? (ObjectNode) root.get(PROJECTS)
                : root.putObject(PROJECTS);
        ObjectNode entry = projects.path(worktree).isObject()
                ? (ObjectNode) projects.get(worktree)
                : projects.putObject(worktree);
        if (entry.path(TRUSTED).asBoolean(false)) {
            return null;
        }
        entry.put(TRUSTED, true);
        return JSON.writeValueAsString(root);
    }

    /** Claude keys the entry by the working directory it resolves, which no symlink survives. */
    private static String resolved(Path worktree) {
        try {
            return worktree.toRealPath().toString();
        } catch (IOException e) {
            return worktree.toString();
        }
    }

    private static void discard(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            log.atWarn().setMessage("staged config left behind")
                    .addKeyValue("file", staged)
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }
}
