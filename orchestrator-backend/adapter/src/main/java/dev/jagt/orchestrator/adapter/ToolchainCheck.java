package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ToolchainCheck implements StartupCheck {

    private final OrchestratorProperties properties;

    @Override
    public List<Problem> problems() {
        List<Problem> problems = new ArrayList<>();
        // Spawned by bare name, so an install only the resolver could find is one jagt never calls.
        if (!Executables.onPath("git")) {
            problems.add(new Problem("git",
                    "git is not on PATH — every worktree, branch and merge jagt makes runs through it."));
        }
        if (Executables.unresolved(Executables.resolve(properties.tmuxCommand()))) {
            problems.add(new Problem("orchestrator.tmux-command", "orchestrator.tmux-command: '" + properties.tmuxCommand() + "' is not on PATH nor in"
                    + " the usual install directories, and every agent runs inside a tmux session. Install"
                    + " tmux or set the key to a full path."));
        }
        return problems;
    }
}
