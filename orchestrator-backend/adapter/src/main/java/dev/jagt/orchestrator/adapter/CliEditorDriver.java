package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class CliEditorDriver implements EditorDriver, StartupCheck {

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;

    @Override
    public List<Problem> problems() {
        return Stream.of(problems(properties.editorCommand(), "orchestrator.editor-command"),
                        diffIsSet() ? problems(properties.editorDiffCommand(), "orchestrator.editor-diff-command")
                                : List.<Problem>of())
                .flatMap(List::stream).toList();
    }

    private static List<Problem> problems(@Nullable List<String> configured, String configKey) {
        try {
            launcher(configured, configKey);
            return List.of();
        } catch (IllegalStateException missing) {
            return List.of(new Problem(configKey, String.valueOf(missing.getMessage())));
        }
    }

    @Override
    public void open(Path path) {
        List<String> command = launcher(properties.editorCommand(), "orchestrator.editor-command");
        command.add(path.toString());
        processRunner.runDetached(null, command);
    }

    private boolean diffIsSet() {
        List<String> diff = properties.editorDiffCommand();
        return diff != null && !diff.isEmpty();
    }

    @Override
    public void openDiff(Path left, Path right) {
        if (!diffIsSet()) {
            throw new IllegalStateException("orchestrator.editor-diff-command is not set — the editor opens a"
                    + " worktree but cannot compare two folders; set it to a folder-diff launcher such as"
                    + " [idea, diff] or [meld].");
        }
        List<String> command = launcher(properties.editorDiffCommand(), "orchestrator.editor-diff-command");
        command.add(left.toString());
        command.add(right.toString());
        processRunner.runDetached(null, command);
    }

    /** The sentence names the key to set, not the binary the human never chose. */
    private static List<String> launcher(@Nullable List<String> configured, String configKey) {
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
}
