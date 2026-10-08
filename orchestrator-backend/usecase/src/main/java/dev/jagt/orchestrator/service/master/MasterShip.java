package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** What the Master presses on a ready round. Closing a task is the human's alone: a round holding nothing waits for them. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterShip {

    /** The words that make the next one a branch; anywhere else {@code dev} is just a word of the task. */
    private static final Set<String> BRANCH_WORDS = Set.of("from", "into", "onto");

    private final WorktreeChanges changes;
    private final CommandService commands;
    private final TaskLauncher launcher;
    private final ConfigService configService;

    /** False where nothing was pressed. */
    public boolean ship(String taskId, TaskState task) {
        if (!changes.anyToShip(task)) {
            return false;
        }
        log.atInfo().setMessage("master ships").addKeyValue("task", taskId).log();
        // Through the same door a human's press uses, so an illegal move is refused rather than taken, and
        // stamped as the Master's so what a ship does on its behalf can differ from what it does on yours.
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.SHIP));
        return true;
    }

    /** A part of the work needing a branch of its own, opened by the {@code do} line a human would type. */
    public String open(String line) {
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
