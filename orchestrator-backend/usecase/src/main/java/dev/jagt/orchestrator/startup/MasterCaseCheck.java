package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

/**
 * A verb no Master case names is never pressed, a step no session case sets off by the Master is never relayed by it,
 * and a Master case naming anything but a verb allows nothing. Read only where a configuration exists and runs the
 * Master: without one no Master moves, and its absence is the configuration's own problem.
 */
@Component
@RequiredArgsConstructor
public class MasterCaseCheck implements StartupCheck {

    private final MasterCases cases;
    private final SessionCases sessionCases;
    private final ConfigService config;
    private final OrchestratorPaths paths;

    @Override
    public List<Problem> problems() {
        if (!Files.exists(paths.configFile()) || !config.load().master().running()) {
            return List.of();
        }
        return Stream.concat(cases.book().problems(MasterMove.pressed(), MasterCases.FILE).stream(),
                sessionCases.book().unnamed(MasterMove.relayed(), CaseBook.By.MASTER).stream()
                        .map(step -> new Problem(SessionCases.FILE, SessionCases.FILE + ": no case by master sends the"
                                + " Master's move '" + step + "' — add one, or the Master never makes it"))).toList();
    }

    @Override
    public boolean machine() {
        return false;
    }
}
