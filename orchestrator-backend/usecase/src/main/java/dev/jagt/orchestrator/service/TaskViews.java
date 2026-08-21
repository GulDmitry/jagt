package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskChoice;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds the ONE projection every human surface renders, so a phase, an owner and a set of legal actions cannot
 * mean one thing in one surface and another in the next.
 *
 * <p>Order is the ALIAS, and it is the projection's job because both surfaces repaint on every state write: an
 * order that follows activity moves a task on the agent's next keep-alive, which is motion nobody asked for and
 * costs a human the position they had learnt. A task therefore moves only when one is created or closed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskViews {

    private final StateService stateService;
    private final ConfigService configService;

    public List<TaskChoice> choices() {
        return stateService.tasks().entrySet().stream()
                .map(e -> new TaskChoice(e.getValue().alias(), e.getKey(), e.getValue().title()))
                .toList();
    }

    /**
     * One render's worth of answers, read from the configuration ONCE. Both surfaces show the tasks AND the
     * polling policy that explains them, and the console redraws on every keystroke — two reads would be two
     * parses per character, and could disagree with each other mid-render.
     */
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
     * Numeric where the alias is (letter, digits): plain text order puts p10 before p2, which is not an order a
     * human can predict. An alias-less task sorts after every aliased one, by id.
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
     * The configuration, or its defaults when the file cannot be read. The console REDRAWS THROUGH HERE on every
     * keystroke, and {@code config.json} is hand-edited while jagt runs — a half-saved file would otherwise throw
     * out of the render loop and take the orchestrator down with it. The failure is logged, not swallowed
     * silently, and the surfaces then show what defaults mean (nothing polling, no deploy branches).
     */
    private ConfigService.ConfigFile readableConfig() {
        try {
            return configService.load();
        } catch (RuntimeException e) {
            log.warn("Could not read the configuration for this render: {}", e.toString());
            return ConfigService.ConfigFile.defaults();
        }
    }
}
