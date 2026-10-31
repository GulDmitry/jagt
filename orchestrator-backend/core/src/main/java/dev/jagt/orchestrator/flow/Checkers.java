package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TaskState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Checkers {

    public static final String QA = "qa";

    private static final String ROUND = "-round";

    private Checkers() {
    }

    /** Where each installed checker stands for the task, in composition order. */
    public static Map<String, CheckerPhase> of(TaskState task) {
        Map<String, CheckerPhase> phases = new LinkedHashMap<>();
        TaskChart.installedCheckers().forEach(role -> phases.put(role, CheckerPhase.of(task, role)));
        return phases;
    }

    /** The first checker whose read is running: it holds the turn. */
    static Optional<String> reading(Map<String, CheckerPhase> checkers) {
        return checkers.entrySet().stream().filter(entry -> entry.getValue() == CheckerPhase.READING)
                .map(Map.Entry::getKey).findFirst();
    }

    /** The role whose round a turn on {@code step} is: {@code <id>-round}. */
    public static Optional<String> roleOfRound(String step) {
        return step.endsWith(ROUND) && step.length() > ROUND.length()
                ? Optional.of(step.substring(0, step.length() - ROUND.length())) : Optional.empty();
    }

    /** The role whose read a turn on {@code step} opens. */
    public static Optional<String> roleOfRead(String step) {
        return QaVerdict.isStep(step) ? Optional.of(QA) : Optional.empty();
    }
}
