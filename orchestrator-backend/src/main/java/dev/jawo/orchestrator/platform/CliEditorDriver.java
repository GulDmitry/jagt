package dev.jawo.orchestrator.platform;

import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.service.ProcessRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
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

    @Override
    public void open(Path path) {
        List<String> command = new ArrayList<>(properties.editorCommand());
        command.add(path.toString());
        processRunner.run(null, Duration.ofSeconds(20), command)
                .expectSuccess(String.join(" ", command));
    }

    @Override
    public void openDiff(Path left, Path right) {
        List<String> command = new ArrayList<>(properties.editorDiffCommand());
        command.add(left.toString());
        command.add(right.toString());
        processRunner.run(null, Duration.ofSeconds(20), command)
                .expectSuccess(String.join(" ", command));
    }
}
