package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Checkers {

    public static final String QA = "qa";

    private Checkers() {
    }

    /** The ids of the installed checkers, in composition order. */
    public static List<String> installed() {
        return TaskChart.installedCheckers();
    }

    /** Where each installed checker stands for the task, in composition order. */
    public static Map<String, CheckerPhase> of(TaskState task) {
        Map<String, CheckerPhase> phases = new LinkedHashMap<>();
        TaskChart.installedCheckers().forEach(role -> phases.put(role, CheckerPhase.of(task, role)));
        return phases;
    }

    /** The installed checkers that run jobs: QA's alone today. */
    public static List<String> withJobs() {
        return TaskChart.installedCheckers().stream().filter(QA::equals).toList();
    }

    /** The first checker whose read is running: it holds the turn. */
    static Optional<String> reading(Map<String, CheckerPhase> checkers) {
        return checkers.entrySet().stream().filter(entry -> entry.getValue() == CheckerPhase.READING)
                .map(Map.Entry::getKey).findFirst();
    }

    /** The role whose read a turn on {@code step} opens. */
    public static Optional<String> roleOfRead(String step) {
        return QaVerdict.isStep(step) ? Optional.of(QA) : Optional.empty();
    }
}
