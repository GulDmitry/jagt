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
import java.util.function.BinaryOperator;

/**
 * A directory Claude has never run in is untrusted, and it discards that directory's whole permission file
 * until a human accepts the trust dialog — so an unattended session stops at prompts nobody answers, and every
 * worktree is such a directory. Claude writes this file too, under a lock this cannot take, so the read and the
 * replace stay adjacent and anything unexpected in it costs the flag rather than the file.
 */
@Slf4j
final class ClaudeTrust {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final String PROJECTS = "projects";
    private static final String TRUSTED = "hasTrustDialogAccepted";
    private static final String UNTRUSTED = "worktree left untrusted";
    private static final String STALE = "trust entry orphaned";

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
        rewrite(configFile, worktree, ClaudeTrust::accepted, UNTRUSTED);
    }

    /** Called while the worktree still exists, so the key matches the one Claude wrote. */
    static void forget(Path configFile, Path worktree) {
        rewrite(configFile, worktree, ClaudeTrust::forgotten, STALE);
    }

    /** Null rather than the unchanged text, so a file nothing was added to is never rewritten. */
    static String accepted(String config, String worktree) {
        ObjectNode root = object(config, worktree, UNTRUSTED);
        if (root == null) {
            return null;
        }
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

    /** The whole entry: a retired worktree is a path nothing will ever run in again. */
    static String forgotten(String config, String worktree) {
        ObjectNode root = object(config, worktree, STALE);
        if (root == null || !root.path(PROJECTS).isObject()) {
            return null;
        }
        ObjectNode projects = (ObjectNode) root.get(PROJECTS);
        if (projects.remove(worktree) == null) {
            return null;
        }
        return JSON.writeValueAsString(root);
    }

    private static ObjectNode object(String config, String worktree, String effect) {
        JsonNode parsed = JSON.readTree(config.isBlank() ? "{}" : config);
        if (!parsed.isObject()) {
            log.atWarn().setMessage("trust config unusable")
                    .addKeyValue("worktree", worktree)
                    .addKeyValue("cause", parsed.getNodeType())
                    .addKeyValue("effect", effect)
                    .log();
            return null;
        }
        return (ObjectNode) parsed;
    }

    private static void rewrite(Path configFile, Path worktree, BinaryOperator<String> edit, String effect) {
        Path staged = null;
        try {
            String current = Files.exists(configFile) ? Files.readString(configFile) : "{}";
            String updated = edit.apply(current, resolved(worktree));
            if (updated == null) {
                return;
            }
            staged = Files.createTempFile(configFile.getParent(), ".claude", ".json");
            Files.writeString(staged, updated);
            Files.move(staged, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JacksonException e) {
            log.atWarn().setMessage("trust config write failed")
                    .addKeyValue("file", configFile)
                    .addKeyValue("worktree", worktree)
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("effect", effect)
                    .log();
        } finally {
            discard(staged);
        }
    }

    /** Claude keys the entry by the resolved working directory, which no symlink survives; a worktree already
     *  deleted must still answer the same key, so its surviving parent resolves it. */
    private static String resolved(Path worktree) {
        try {
            return worktree.toRealPath().toString();
        } catch (IOException gone) {
            Path parent = worktree.getParent();
            if (parent == null) {
                return worktree.toString();
            }
            try {
                return parent.toRealPath().resolve(worktree.getFileName()).toString();
            } catch (IOException e) {
                return worktree.toString();
            }
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
