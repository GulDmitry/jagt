package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaEnvironment;
import dev.jagt.orchestrator.task.QaVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** What a QA run is told: the brief, then the facts of the deploy it checks. */
@Component
@RequiredArgsConstructor
public class CheckerPrompts {

    private final RoleDefinition role;
    private final RoleFiles brief;

    public CheckerReader.Run accept(String taskId, String worktree, String sha, RoleConfig qa, String step) {
        String environments = qa.environmentsOrEmpty().stream().map(CheckerPrompts::environment)
                .collect(Collectors.joining("\n"));
        String facts = deployFact(taskId, sha) + "\nCheck it on:\n" + environments;
        return new CheckerReader.Run(brief.text(), brief.section(step) + "\n\n" + facts, Path.of(worktree), sha,
                qa.modelOr(role.model()), reachable(qa), role.answer().json(), role.id());
    }

    /** The round QA sent back and the reason the session gave for changing nothing, read against fresh evidence. */
    public CheckerReader.Run decline(String taskId, String worktree, String sha, RoleConfig qa, String rows, String reason) {
        String environments = qa.environmentsOrEmpty().stream().map(CheckerPrompts::environment)
                .collect(Collectors.joining("\n"));
        String facts = deployFact(taskId, sha) + "\nThe rows you found not met:\n" + rows
                + "\nThe session changed nothing, and says:\n" + reason + "\nCheck on:\n" + environments;
        return new CheckerReader.Run(brief.text(), brief.section(QaVerdict.DECLINE) + "\n\n" + facts, Path.of(worktree), sha,
                qa.modelOr(role.model()), reachable(qa), role.decline().json(), role.id());
    }

    private static String deployFact(String taskId, String sha) {
        return taskId + "'s deploy was pushed at " + sha + "; whether it landed is yours to prove.";
    }

    private static List<String> reachable(RoleConfig qa) {
        return qa.environmentsOrEmpty().stream()
                .flatMap(environment -> Stream.of(environment.url(), environment.landed()))
                .filter(url -> url != null && !url.isBlank()).distinct().toList();
    }

    private static String environment(QaEnvironment environment) {
        return "- " + environment.url() + (environment.landed() == null || environment.landed().isBlank() ? ""
                : " (the change is up when " + environment.landed() + " answers)");
    }
}
