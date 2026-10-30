package dev.jagt.orchestrator.task;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Every step jagt tells a session, by the id its brief section, its cases and the turn it opens carry. */
public enum Step {
    START("start"),
    REVIVE("revive"),
    CONTEXT("context"),
    PLAN("plan"),
    PLAN_NOT_READY("plan-not-ready"),
    PLAN_ANSWERED("plan-answered"),
    ROUND_NOT_READY("round-not-ready"),
    ASKED_HUMAN("asked-human"),
    ANSWERED("answered"),
    ANSWERED_UNDER("answered-under"),
    REPO("repo"),
    VERIFY("verify"),
    PROBES("probes"),
    SHIP("ship"),
    ROUND_CHECKS("round-checks"),
    ROUND_THREADS("round-threads"),
    ROUND_CHECKS_DONE("round-checks-done"),
    ROUND_THREADS_DONE("round-threads-done"),
    DEPLOY("deploy"),
    CONFLICT("conflict"),
    RESTARTED("restarted"),
    WAKE("wake"),
    WAIT_OVER("wait-over");

    private static final String JOINT = "+";

    private final String id;

    Step(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<Step> find(String id) {
        return Arrays.stream(values()).filter(step -> step.id.equals(id)).findFirst();
    }

    public static Step of(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("no step has the id '" + id + "'"));
    }

    /** The id a turn carries for steps told together. */
    public static String joined(List<Step> steps) {
        return steps.stream().map(Step::id).collect(Collectors.joining(JOINT));
    }
}
