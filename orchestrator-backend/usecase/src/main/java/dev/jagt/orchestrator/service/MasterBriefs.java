package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.PromptTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/** The two briefs a Master reader judges by: its own, and the one the session worked to. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterBriefs {

    private final OrchestratorPaths paths;
    private final PromptTemplates prompts;

    public Optional<String> master(String taskId, ConfigService.ConfigFile.MasterConfig config) {
        try {
            return Optional.of(Files.readString(paths.root().resolve(config.briefOrDefault())));
        } catch (IOException unreadable) {
            log.atError().setMessage("master brief unreadable").addKeyValue("task", taskId)
                    .addKeyValue("file", config.briefOrDefault())
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }

    public String author() {
        return prompts.subAgentContext();
    }
}
