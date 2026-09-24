package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.MasterMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Master session is EXPERIMENTAL and off unless a human turned it on. What it judges lives in a file rather
 * than in a session, so a mode that names no readable one would run a reviewer with no standards.
 */
@Component
@RequiredArgsConstructor
public class MasterCheck implements StartupCheck {

    private final ConfigService configService;
    private final OrchestratorPaths paths;

    @Override
    public List<String> problems() {
        ConfigService.ConfigFile.MasterConfig master = configService.load().master();
        List<String> problems = new ArrayList<>();
        if (MasterMode.of(master.mode()).isEmpty()) {
            problems.add("orchestrator.master.mode: '" + master.mode() + "' is not one of "
                    + MasterMode.ids());
        }
        if (!master.running()) {
            return List.copyOf(problems);
        }
        Path brief = brief(master.briefOrDefault());
        if (!Files.isRegularFile(brief)) {
            problems.add("orchestrator.master.brief: no file at " + brief
                    + " — copy master-brief.md.dist to it and edit it until it reads like you");
        }
        return List.copyOf(problems);
    }

    /** Relative to the orchestrator root, like everything else a human writes into `jagt.yml`. */
    private Path brief(String declared) {
        Path named = Path.of(declared);
        return named.isAbsolute() ? named : paths.root().resolve(named);
    }
}
