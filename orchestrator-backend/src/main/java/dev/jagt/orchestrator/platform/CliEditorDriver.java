package dev.jagt.orchestrator.platform;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Default editor strategy: any CLI launcher taking a path — configured as a
 * command list in `orchestrator.editor-command`, e.g. [open, -a, IntelliJ IDEA]
 * or [code] or [subl]. The worktree path is appended as the last argument.
 */
@Component
public class CliEditorDriver implements EditorDriver {

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;

    public CliEditorDriver(ProcessRunner processRunner, OrchestratorProperties properties) {
        this.processRunner = processRunner;
        this.properties = properties;
    }

    // Detached: GUI launchers (idea, open -a) may not return until the IDE is ready or the window
    // closes; waiting would time out and then kill the window we just opened.
    @Override
    public void open(Path path) {
        List<String> command = new ArrayList<>(properties.editorCommand());
        command.add(path.toString());
        processRunner.runDetached(null, command);
    }

    @Override
    public void openDiff(Path left, Path right) {
        List<String> command = new ArrayList<>(properties.editorDiffCommand());
        command.add(left.toString());
        command.add(right.toString());
        processRunner.runDetached(null, command);
    }

    private static final Logger log = LoggerFactory.getLogger(CliEditorDriver.class);

    /**
     * Drop the worktree from every JetBrains IDE's recent-projects list (macOS config location), so a
     * `done` task doesn't leave a dead "project" on the Welcome screen. Best-effort: JetBrains owns the
     * file and rewrites it from memory, so this reliably takes effect after the IDE restarts. No-op when
     * the JetBrains config dir is absent (a non-JetBrains editor) — the path simply isn't in any file.
     */
    @Override
    public void forgetProject(Path worktreePath) {
        String userHome = System.getProperty("user.home");
        Path jetBrains = Path.of(userHome, "Library", "Application Support", "JetBrains");
        if (!Files.isDirectory(jetBrains)) {
            return;
        }
        try (var ideDirs = Files.newDirectoryStream(jetBrains)) {
            for (Path ideDir : ideDirs) {
                Path recent = ideDir.resolve("options").resolve("recentProjects.xml");
                if (!Files.isRegularFile(recent)) {
                    continue;
                }
                try {
                    String xml = Files.readString(recent);
                    // Remove ONLY the worktree this `done` deleted — one entry, one-to-one. It does NOT
                    // garbage-collect other dead entries: `done` cleans up after itself, nothing else.
                    String pruned = pruneRecentProjects(xml, userHome, worktreePath);
                    if (!pruned.equals(xml)) {
                        Path tmp = recent.resolveSibling(recent.getFileName() + ".jagt.tmp");
                        Files.writeString(tmp, pruned);
                        Files.move(tmp, recent, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        log.info("Removed {} from {}", worktreePath, recent);
                    }
                } catch (IOException e) {
                    log.debug("Could not prune {}: {}", recent, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("Could not scan JetBrains config: {}", e.getMessage());
        }
    }

    /**
     * Remove the {@code <entry key="…">…</entry>} block for {@code worktree} from a recentProjects.xml body.
     * JetBrains stores the path either absolute or with a {@code $USER_HOME$} macro, so both forms are tried.
     * Pure + package-private for tests.
     */
    static String pruneRecentProjects(String xml, String userHome, Path worktree) {
        String abs = worktree.toAbsolutePath().normalize().toString();
        List<String> keys = new ArrayList<>(List.of(abs));
        if (abs.startsWith(userHome)) {
            keys.add("$USER_HOME$" + abs.substring(userHome.length()));
        }
        String out = xml;
        for (String key : keys) {
            out = out.replaceAll("(?s)\\s*<entry key=\"" + Pattern.quote(key) + "\"[^>]*>.*?</entry>", "");
        }
        return out;
    }
}
