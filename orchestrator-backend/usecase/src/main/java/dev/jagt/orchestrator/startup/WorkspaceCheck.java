package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The two places jagt itself owns. A state file it cannot write is found on the first task, not here. */
@Component
@RequiredArgsConstructor
public class WorkspaceCheck implements StartupCheck {

    private final OrchestratorPaths paths;

    @Override
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (!Files.isDirectory(paths.root())) {
            problems.add("orchestrator.root " + paths.root() + " is not a directory (ORCHESTRATOR_ROOT).");
        }
        Path stateDir = paths.stateFile().getParent();
        if (stateDir != null && !Files.isDirectory(stateDir)) {
            problems.add("orchestrator.state-file " + paths.stateFile() + ": " + stateDir
                    + " is not a directory.");
        } else if (stateDir != null && !Files.isWritable(stateDir)) {
            problems.add("orchestrator.state-file " + paths.stateFile() + ": " + stateDir
                    + " is not writable, so no task could ever be recorded.");
        } else if (Files.exists(paths.stateFile()) && !Files.isWritable(paths.stateFile())) {
            problems.add("orchestrator.state-file " + paths.stateFile() + " is not writable.");
        }
        return problems;
    }
}
