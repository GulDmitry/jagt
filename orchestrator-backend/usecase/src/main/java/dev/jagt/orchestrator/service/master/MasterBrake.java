package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stops `act` after {@link #STRIKES} tasks in a row the Master moved ended badly; a deploy resets the count. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterBrake {

    static final int STRIKES = 3;

    private final StateService stateService;
    private final OrchestratorPaths paths;
    private final Map<String, Seen> seen = new HashMap<>();
    private final Set<String> strikes = new LinkedHashSet<>();

    private record Seen(long step, long silence) {

        static Seen of(TaskState task) {
            return new Seen(task.history().isEmpty() ? 0 : task.history().getLast().at(), task.silentSince());
        }
    }

    private record Ending(String task, long at, boolean bad, String why) {
    }

    /** Only what happens after this start counts. */
    @PostConstruct
    void follow() {
        stateService.tasks().forEach((id, task) -> seen.put(id, Seen.of(task)));
        stateService.onChange(written -> observe(written.tasks()));
    }

    synchronized void observe(Map<String, TaskState> tasks) {
        List<Ending> endings = new ArrayList<>();
        tasks.forEach((id, task) -> {
            if (movedByTheMaster(task)) {
                endings.addAll(endings(id, task, seen.getOrDefault(id, new Seen(0, 0))));
            }
            seen.put(id, Seen.of(task));
        });
        seen.keySet().retainAll(tasks.keySet());
        endings.stream().sorted(Comparator.comparingLong(Ending::at)).forEach(this::count);
    }

    public boolean stopped() {
        return Files.exists(paths.actStopped());
    }

    /** True where it was stopped. */
    public synchronized boolean restore() {
        strikes.clear();
        try {
            boolean was = Files.deleteIfExists(paths.actStopped());
            log.atInfo().setMessage("master act restored").addKeyValue("wasStopped", was).log();
            return was;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove " + paths.actStopped(), e);
        }
    }

    private static boolean movedByTheMaster(TaskState task) {
        return task.history().stream().anyMatch(step -> step.origin() == ActionOrigin.MASTER);
    }

    private static boolean silenceFollowsTheMaster(TaskState task) {
        return task.history().stream().filter(step -> step.at() <= task.silentSince())
                .reduce((first, second) -> second).map(StatusChange::origin).orElse(null) == ActionOrigin.MASTER;
    }

    private static List<Ending> endings(String id, TaskState task, Seen was) {
        List<Ending> endings = new ArrayList<>();
        for (StatusChange step : task.history()) {
            if (step.at() <= was.step()) {
                continue;
            }
            switch (step.status()) {
                case DEPLOYED -> endings.add(new Ending(id, step.at(), false, "deployed"));
                case DEPLOY_CONFLICT, REVERTED -> endings.add(new Ending(id, step.at(), true, step.status().label()));
                default -> { }
            }
        }
        if (task.agentIsSilent() && task.silentSince() != was.silence() && silenceFollowsTheMaster(task)) {
            endings.add(new Ending(id, task.silentSince(), true, "silent: " + task.silentBecause()));
        }
        return endings;
    }

    private void count(Ending ending) {
        if (!ending.bad()) {
            strikes.clear();
            return;
        }
        strikes.add(ending.task());
        log.atInfo().setMessage("master strike").addKeyValue("task", ending.task())
                .addKeyValue("why", ending.why()).addKeyValue("strikes", strikes.size()).log();
        if (strikes.size() >= STRIKES) {
            stop();
        }
    }

    private void stop() {
        try {
            Files.writeString(paths.actStopped(), String.join("\n", strikes) + "\n");
            log.atWarn().setMessage("master act stopped").addKeyValue("tasks", List.copyOf(strikes))
                    .addKeyValue("cause", STRIKES + " tasks in a row the Master moved ended badly")
                    .addKeyValue("effect", "the Master judges until the human runs `act`").log();
        } catch (IOException e) {
            log.atError().setMessage("master act not stopped").addKeyValue("file", paths.actStopped())
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "act keeps pressing").log();
        }
        strikes.clear();
    }
}
