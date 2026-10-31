package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

/** The QA files are read only where {@code roles.qa.enabled} is on, so a disabled QA can never fail a start. */
@Component
@RequiredArgsConstructor
public class QaCaseCheck implements StartupCheck {

    private final RoleFiles files;
    private final ConfigService config;
    private final OrchestratorPaths paths;

    @Override
    public List<Problem> problems() {
        if (!Files.exists(paths.configFile()) || !config.load().role(Checkers.QA).enabledOrDefault()) {
            return List.of();
        }
        return Stream.concat(files.book().problems(files.steps(), files.casesFile()).stream(), files.unreadable().stream())
                .toList();
    }

    @Override
    public boolean machine() {
        return false;
    }
}
