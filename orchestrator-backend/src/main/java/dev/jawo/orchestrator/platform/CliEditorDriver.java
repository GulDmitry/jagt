package dev.jawo.orchestrator.platform;

import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.service.ProcessRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
}
