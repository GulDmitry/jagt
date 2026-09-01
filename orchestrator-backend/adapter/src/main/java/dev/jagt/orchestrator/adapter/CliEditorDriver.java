package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.config.OrchestratorProperties;
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

    /** The sentence names the key to set, not the binary the human never chose. */
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
        List<String> command = new ArrayList<>(configured);
        command.set(0, binary);
        return command;
    }

    /** Best-effort and IMMEDIATE only: a live IDE holds this list in memory and flushes it back on its next save
     *  or exit, resurrecting the entry. */
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

    private void rewriteRecentProjects(String userHome, Function<String, String> prune) {
        for (Path jetBrains : jetBrainsConfigDirs(userHome)) {
            if (Files.isDirectory(jetBrains)) {
                rewriteRecentProjectsIn(jetBrains, prune);
            }
        }
    }

    /** Both locations are probed rather than switched on an OS flag: only one exists on a given machine. */
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
                        log.atInfo().setMessage("jetbrains recent projects pruned")
                                .addKeyValue("file", recent)
                                .log();
                    }
                } catch (IOException e) {
                    log.atDebug().setMessage("jetbrains prune failed")
                            .addKeyValue("file", recent)
                            .addKeyValue("cause", e.toString())
                            .log();
                }
            }
        } catch (IOException e) {
            log.atDebug().setMessage("jetbrains config scan failed")
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }

    private static final Pattern ENTRY_KEY = Pattern.compile("<entry key=\"([^\"]*)\"");

    /** JetBrains stores the path either absolute or with a {@code $USER_HOME$} macro, so both forms are tried. */
    static String pruneRecentProjects(String xml, String userHome, Path worktree) {
        String abs = worktree.toAbsolutePath().normalize().toString();
        List<String> keys = new ArrayList<>(List.of(abs));
        if (abs.startsWith(userHome)) {
            keys.add("$USER_HOME$" + abs.substring(userHome.length()));
        }
        return removeEntries(xml, keys);
    }

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

    static String removeEntries(String xml, Collection<String> rawKeys) {
        String out = xml;
        for (String key : rawKeys) {
            out = out.replaceAll("(?s)\\s*<entry key=\"" + Pattern.quote(key) + "\"[^>]*>.*?</entry>", "");
        }
        return out;
    }
}
