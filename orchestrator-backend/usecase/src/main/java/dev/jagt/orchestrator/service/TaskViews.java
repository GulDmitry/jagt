package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds the ONE projection a human surface renders. Order is the ALIAS, and it is the projection's job because the
 * board repaints on every state write: an order that followed activity would move a task on every keep-alive.
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
                .sorted(Comparator.comparing(TaskViews::aliasOrder))
                .map(entry -> TaskView.of(entry.getKey(), entry.getValue(),
                        ReviewDrafts.pending(entry.getValue(), entry.getValue().status(),
                                config.codeReview().shipPostsEveryDraft()),
                        cadence.watch(entry.getValue(), now), deployBranches))
                .toList();
        return new Snapshot(views, cadence, List.copyOf(config.projects().keySet()));
    }

    /**
     * Numeric where the alias is: plain text order puts p10 before p2. An alias-less task sorts after every aliased
     * one, by id.
     */
    private static String aliasOrder(Map.Entry<String, TaskState> entry) {
        String alias = entry.getValue().alias();
        if (alias == null || alias.isBlank()) {
            return "~" + entry.getKey();
        }
        String digits = alias.replaceAll("\\D", "");
        return alias.replaceAll("\\d", "") + (digits.isEmpty() ? "" : "%09d".formatted(Long.parseLong(digits)));
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
