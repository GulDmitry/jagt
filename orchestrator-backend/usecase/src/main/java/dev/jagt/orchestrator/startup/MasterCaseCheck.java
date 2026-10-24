package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.List;

/**
 * A move no case names is never made, and a case naming a move the Master lacks allows nothing. Read only where a
 * configuration exists and runs the Master: without one no Master moves, and its absence is the configuration's
 * own problem.
 */
@Component
@RequiredArgsConstructor
public class MasterCaseCheck implements StartupCheck {

    private final MasterCases cases;
    private final ConfigService config;
    private final OrchestratorPaths paths;

    @Override
    public List<Problem> problems() {
        return Files.exists(paths.configFile()) && config.load().master().running()
                ? cases.book().problems(MasterMove.ids(), MasterCases.FILE) : List.of();
    }

    @Override
    public boolean machine() {
        return false;
    }
}
