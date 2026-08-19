package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.StartupCheck;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default editor strategy: any CLI launcher taking a path — configured as a
 * command list in `orchestrator.editor-command`, e.g. [open, -a, IntelliJ IDEA]
 * or [code] or [subl]. The worktree path is appended as the last argument.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CliEditorDriver implements EditorDriver, StartupCheck {

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;

    @Override
    public List<String> problems() {
        return Stream.of(problems(properties.editorCommand(), "orchestrator.editor-command"),
                        problems(properties.editorDiffCommand(), "orchestrator.editor-diff-command"))
                .flatMap(List::stream).toList();
    }

    private static List<String> problems(List<String> configured, String configKey) {
        try {
            launcher(configured, configKey);
            return List.of();
        } catch (IllegalStateException missing) {
            return List.of(missing.getMessage());
        }
    }

    // Detached: GUI launchers (idea, open -a) may not return until the IDE is ready or the window
    // closes; waiting would time out and then kill the window we just opened.
    @Override
    public void open(Path path) {
        List<String> command = launcher(properties.editorCommand(), "orchestrator.editor-command");
        command.add(path.toString());
        processRunner.runDetached(null, command);
    }

    @Override
    public void openDiff(Path left, Path right) {
        List<String> command = launcher(properties.editorDiffCommand(), "orchestrator.editor-diff-command");
        command.add(left.toString());
        command.add(right.toString());
        processRunner.runDetached(null, command);
    }

    /**
     * A launcher nobody has is the human's to fix, so the sentence names the key to set — not the binary they
     * never chose, which is all a failed spawn could report.
     */
    private static List<String> launcher(List<String> configured, String configKey) {
        if (configured == null || configured.isEmpty() || configured.getFirst().isBlank()) {
            throw new IllegalStateException(configKey + " is empty — set it to the launcher of the editor you"
                    + " want jagt to open worktrees with.");
        }
        String binary = Executables.resolve(configured.getFirst());
        if (Executables.unresolved(binary)) {
            throw new IllegalStateException(configKey + ": '" + binary + "' is not on PATH nor in the usual"
                    + " install directories — install its launcher or set the key to a full path.");
        }
        // The RESOLVED launcher, and the human's own arguments after it: a desktop launcher lives in no bin
        // directory, so spawning the bare name is how the editor stops opening.
        List<String> command = new ArrayList<>(configured);
        command.set(0, binary);
        return command;
    }

    /**
     * Drop the worktree from every JetBrains IDE's recent-projects list (macOS config location), so a
     * `done` task doesn't leave a dead "project" on the Welcome screen. Best-effort and IMMEDIATE only:
     * while the IDE is live it holds the list in memory and flushes it back on its next save/exit, which
     * resurrects the entry — even across a restart (the entry is re-written on the way down). The scheduled
     * {@link #forgetDeadWorktrees} GC is what actually makes the removal stick once the IDE is next closed.
     */
    @Override
    public void forgetProject(Path worktreePath) {
        String userHome = System.getProperty("user.home");
        rewriteRecentProjects(userHome, xml -> pruneRecentProjects(xml, userHome, worktreePath));
    }

    @Override
    public void forgetDeadWorktrees(List<WorktreeLocation> locations) {
        if (locations.isEmpty()) {
            return;
        }
        String userHome = System.getProperty("user.home");
        rewriteRecentProjects(userHome,
                xml -> removeEntries(xml, deadWorktreeKeys(xml, userHome, locations, Files::isDirectory)));
    }

    /**
     * Apply {@code prune} to every JetBrains IDE's recentProjects.xml, atomically writing back only files it
     * actually changed. Best-effort: JetBrains owns the file and rewrites it from memory while running, so a
     * write reliably sticks only once that IDE is next closed. No-op when no JetBrains config dir is present
     * (a non-JetBrains editor).
     */
    private void rewriteRecentProjects(String userHome, Function<String, String> prune) {
        for (Path jetBrains : jetBrainsConfigDirs(userHome)) {
            if (Files.isDirectory(jetBrains)) {
                rewriteRecentProjectsIn(jetBrains, prune);
            }
        }
    }

    /**
     * Where JetBrains keeps per-IDE config, per platform: {@code ~/Library/Application Support/JetBrains} on
     * macOS, {@code ~/.config/JetBrains} on Linux (XDG). Both are probed rather than switched on an OS flag —
     * only one of them exists on a given machine, and probing keeps this free of an `if macos`.
     */
    static List<Path> jetBrainsConfigDirs(String userHome) {
        return List.of(Path.of(userHome, "Library", "Application Support", "JetBrains"),
                Path.of(userHome, ".config", "JetBrains"));
    }

    private void rewriteRecentProjectsIn(Path jetBrains, Function<String, String> prune) {
        try (var ideDirs = Files.newDirectoryStream(jetBrains)) {
            for (Path ideDir : ideDirs) {
                Path recent = ideDir.resolve("options").resolve("recentProjects.xml");
                if (!Files.isRegularFile(recent)) {
                    continue;
                }
                try {
                    String xml = Files.readString(recent);
                    String pruned = prune.apply(xml);
                    if (!pruned.equals(xml)) {
                        Path tmp = recent.resolveSibling(recent.getFileName() + ".jagt.tmp");
                        Files.writeString(tmp, pruned);
                        Files.move(tmp, recent, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        log.info("Pruned dead worktree entries from {}", recent);
                    }
                } catch (IOException e) {
                    log.debug("Could not prune {}: {}", recent, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("Could not scan JetBrains config: {}", e.getMessage());
        }
    }

    private static final Pattern ENTRY_KEY = Pattern.compile("<entry key=\"([^\"]*)\"");

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
        return removeEntries(xml, keys);
    }

    /**
     * The raw entry keys in {@code xml} whose directory no longer exists AND that name a jagt worktree
     * ({@code <taskId>-<projectKey>} or {@code <taskId>-deploy} sibling of a configured project). Live
     * projects — and any dead entry outside a jagt worktree location — are left alone. Pure (existence
     * injected via {@code dirExists}) + package-private for tests.
     */
    static List<String> deadWorktreeKeys(String xml, String userHome,
                                         List<WorktreeLocation> locations, Predicate<Path> dirExists) {
        List<String> keys = new ArrayList<>();
        Matcher m = ENTRY_KEY.matcher(xml);
        while (m.find()) {
            String rawKey = m.group(1);
            Path path = Path.of(rawKey.replace("$USER_HOME$", userHome)).toAbsolutePath().normalize();
            if (!dirExists.test(path) && isJagtWorktree(path, locations)) {
                keys.add(rawKey);
            }
        }
        return keys;
    }

    private static boolean isJagtWorktree(Path path, List<WorktreeLocation> locations) {
        Path parent = path.getParent();
        String name = path.getFileName().toString();
        for (WorktreeLocation loc : locations) {
            if (loc.parentDir().equals(parent)
                    && (name.endsWith("-" + loc.projectKey()) || name.endsWith("-deploy"))) {
                return true;
            }
        }
        return false;
    }

    /** Strip each named {@code <entry key="…">…</entry>} block from a recentProjects.xml body. Pure. */
    static String removeEntries(String xml, Collection<String> rawKeys) {
        String out = xml;
        for (String key : rawKeys) {
            out = out.replaceAll("(?s)\\s*<entry key=\"" + Pattern.quote(key) + "\"[^>]*>.*?</entry>", "");
        }
        return out;
    }
}
