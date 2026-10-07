package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The two briefs a Master reader judges by: its own, and the one the session worked to. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterBriefs {

    private final OrchestratorPaths paths;
    private final PromptTemplates prompts;

    private static final String SHIPPED = ".dist";

    public Optional<String> master(String taskId, ConfigService.ConfigFile.MasterConfig config) {
        try {
            return Optional.of(Files.readString(file(config)));
        } catch (IOException unreadable) {
            log.atError().setMessage("master brief unreadable").addKeyValue("task", taskId)
                    .addKeyValue("file", config.briefOrDefault())
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }

    /** Where none was named or copied, the shipped one: judging by default asks for no setup. */
    public Path file(ConfigService.ConfigFile.MasterConfig config) {
        Path named = paths.root().resolve(config.briefOrDefault());
        boolean unnamed = config.brief() == null || config.brief().isBlank();
        return unnamed && !Files.exists(named) ? paths.root().resolve(config.briefOrDefault() + SHIPPED) : named;
    }

    public String author() {
        return prompts.subAgentContext();
    }
}
