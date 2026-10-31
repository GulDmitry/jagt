package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.stream.Collectors;

/** What a QA run is told: the brief, then the facts of the deploy it checks. */
@Component
@RequiredArgsConstructor
public class QaPrompts {

    private static final String ACCEPT = "accept";

    private final QaBrief brief;

    public QaReader.Run accept(String taskId, String worktree, String sha, QaConfig qa) {
        String environments = qa.environmentsOrEmpty().stream().map(QaPrompts::environment)
                .collect(Collectors.joining("\n"));
        String facts = taskId + " is deployed at " + sha + ".\nCheck it on:\n" + environments;
        return new QaReader.Run(brief.text(), brief.section(ACCEPT) + "\n\n" + facts, Path.of(worktree), sha,
                qa.modelOrDefault());
    }

    private static String environment(QaEnvironment environment) {
        return "- " + environment.url() + (environment.landed() == null || environment.landed().isBlank() ? ""
                : " (the change is up when " + environment.landed() + " answers)");
    }
}
