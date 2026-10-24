package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final Rounds rounds;
    private final Clock clock;

    /** One render's worth of answers, read from the configuration ONCE so two reads cannot disagree mid-render. */
    public record Snapshot(List<TaskView> tasks, AutoReviewCadence cadence, List<String> projects,
                           @Nullable ActStop actStop, String masterMode) {
    }

    public Snapshot snapshot() {
        return snapshot(stateService.tasks());
    }

    private Snapshot snapshot(Map<String, TaskState> tasks) {
        ConfigService.ConfigFile config = readableConfig();
        Optional<ActStop> stop = configService.actStop();
        AutoReviewCadence cadence = AutoReviewCadence.from(config.autoReview());
        long now = clock.millis();
        Map<String, String> deployBranches = new java.util.LinkedHashMap<>();
        config.projects().forEach((key, project) -> deployBranches.put(key, project.deployBranch()));
        List<TaskView> views = tasks.entrySet().stream()
                .map(entry -> TaskView.of(entry.getKey(), entry.getValue(), rounds.of(entry.getValue(), config),
                        cadence.watch(entry.getValue(), now), deployBranches))
                .toList();
        return new Snapshot(views, cadence, List.copyOf(config.projects().keySet()), stop.orElse(null),
                config.master().modeOrDefault().id());
    }

    public List<TaskView> all() {
        return snapshot().tasks();
    }

    /** A task per line, without its history or spend. */
    public String listing() {
        Map<String, TaskState> tasks = stateService.tasks();
        String listing = snapshot(tasks).tasks().stream().map(view -> listed(tasks.get(view.id()), view))
                .collect(Collectors.joining("\n"));
        return listing.isEmpty() ? "no tasks" : listing;
    }

    private String listed(@Nullable TaskState task, TaskView view) {
        String tail = Optional.ofNullable(task).map(t -> (t.title() == null ? "" : " " + t.title())
                + (t.message() == null || t.message().isBlank() ? "" : " — " + t.message())
                + (t.report() == AgentReport.PLAIN ? "" : " report=" + t.report().name().toLowerCase(Locale.ROOT))
                + t.repos().stream().map(repo -> " | " + repo.worktreePath()
                        + (repo.mrUrl() == null ? "" : " " + repo.mrUrl())).collect(Collectors.joining())).orElse("");
        return view.id() + " [" + view.status() + "]" + tail + " alias=" + (view.alias() == null ? "-" : view.alias())
                + " legal=" + view.actions().stream().map(TaskView.ActionView::id).collect(Collectors.joining(","));
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
