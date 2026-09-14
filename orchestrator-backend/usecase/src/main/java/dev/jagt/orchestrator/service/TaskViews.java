package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds the ONE projection a human surface renders, in the order the tasks were REGISTERED: an alias is reused the
 * moment it is freed, so ordering by one drops a new task into the gap a retired one left. Which order a surface
 * shows is then the surface's own answer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskViews {

    private final StateService stateService;
    private final ConfigService configService;

    /** One render's worth of answers, read from the configuration ONCE so two reads cannot disagree mid-render. */
    public record Snapshot(List<TaskView> tasks, AutoReviewCadence cadence, List<String> projects) {
    }

    public Snapshot snapshot() {
        ConfigService.ConfigFile config = readableConfig();
        AutoReviewCadence cadence = AutoReviewCadence.from(config.autoReview());
        long now = System.currentTimeMillis();
        Map<String, String> deployBranches = new java.util.LinkedHashMap<>();
        config.projects().forEach((key, project) -> deployBranches.put(key, project.deployBranch()));
        List<TaskView> views = stateService.tasks().entrySet().stream()
                .map(entry -> TaskView.of(entry.getKey(), entry.getValue(),
                        ReviewDrafts.pending(entry.getValue(), entry.getValue().status(),
                                config.codeReview().shipPostsEveryDraft()),
                        cadence.watch(entry.getValue(), now), deployBranches))
                .toList();
        return new Snapshot(views, cadence, List.copyOf(config.projects().keySet()));
    }

    public List<TaskView> all() {
        return snapshot().tasks();
    }

    /**
     * The configuration, or its defaults when the file cannot be read: {@code jagt.yml} is hand-edited while jagt
     * runs, and a half-saved file would throw out of a render loop. The failure is logged, never swallowed.
     */
    private ConfigService.ConfigFile readableConfig() {
        try {
            return configService.load();
        } catch (RuntimeException e) {
            log.atWarn().setMessage("config read failed")
                    .addKeyValue("scope", "render")
                    .addKeyValue("cause", e.toString())
                    .log();
            return ConfigService.ConfigFile.defaults();
        }
    }
}
