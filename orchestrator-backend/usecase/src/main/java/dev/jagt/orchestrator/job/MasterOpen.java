package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** A part of the work needing a branch of its own, opened by the {@code do} line a human would type. */
@Component
@RequiredArgsConstructor
@Slf4j
class MasterOpen {

    /** The words that make the next one a branch; anywhere else {@code dev} is just a word of the task. */
    private static final Set<String> BRANCH_WORDS = Set.of("from", "into", "onto");

    private final TaskLauncher launcher;
    private final ConfigService configService;

    String open(String line) {
        if (namesTheDeployBranch(line)) {
            log.atWarn().setMessage("master open refused").addKeyValue("line", line).log();
            return "Not opened: work on the deploy branch is the task's own `deploy`, once its request is green.";
        }
        log.atInfo().setMessage("master opens a task").addKeyValue("line", line).log();
        return launcher.launchLine(line).message();
    }

    private boolean namesTheDeployBranch(String line) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        String project = LaunchRequest.ofLine(line, projects.keySet()).project();
        String deployBranch = project == null || !projects.containsKey(project) ? ""
                : ProjectConfig.localName(projects.get(project).deployBranch());
        List<String> words = List.of(line.split("[\\s,.;:`'\"]+"));
        return !deployBranch.isEmpty() && IntStream.range(1, words.size())
                .filter(i -> BRANCH_WORDS.contains(words.get(i - 1).toLowerCase(Locale.ROOT)))
                .mapToObj(words::get).map(ProjectConfig::localName).anyMatch(deployBranch::equals);
    }
}
