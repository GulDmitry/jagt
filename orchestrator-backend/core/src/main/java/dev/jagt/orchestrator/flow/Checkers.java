package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.QaVerdict;

import java.util.Optional;

public final class Checkers {

    public static final String QA = "qa";

    private static final String ROUND = "-round";

    private Checkers() {
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
