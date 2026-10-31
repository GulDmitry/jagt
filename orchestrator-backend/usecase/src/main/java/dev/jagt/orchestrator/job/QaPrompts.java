package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.QaBrief;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import dev.jagt.orchestrator.task.QaVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.stream.Collectors;

/** What a QA run is told: the brief, then the facts of the deploy it checks. */
@Component
@RequiredArgsConstructor
public class QaPrompts {

    private final QaBrief brief;

    public QaReader.Run accept(String taskId, String worktree, String sha, QaConfig qa, String step) {
        String environments = qa.environmentsOrEmpty().stream().map(QaPrompts::environment)
                .collect(Collectors.joining("\n"));
        String facts = taskId + " is deployed at " + sha + ".\nCheck it on:\n" + environments;
        return new QaReader.Run(brief.text(), brief.section(step) + "\n\n" + facts, Path.of(worktree), sha,
                qa.modelOrDefault());
    }

    /** The round QA sent back and the reason the session gave for changing nothing, read against fresh evidence. */
    public QaReader.Run decline(String taskId, String worktree, String sha, QaConfig qa, String rows, String reason) {
        String environments = qa.environmentsOrEmpty().stream().map(QaPrompts::environment)
                .collect(Collectors.joining("\n"));
        String facts = taskId + " is deployed at " + sha + ".\nThe rows you found not met:\n" + rows
                + "\nThe session changed nothing, and says:\n" + reason + "\nCheck on:\n" + environments;
        return new QaReader.Run(brief.text(), brief.section(QaVerdict.DECLINE) + "\n\n" + facts, Path.of(worktree), sha,
                qa.modelOrDefault());
    }

    private static String environment(QaEnvironment environment) {
        return "- " + environment.url() + (environment.landed() == null || environment.landed().isBlank() ? ""
                : " (the change is up when " + environment.landed() + " answers)");
    }
}
