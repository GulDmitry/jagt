package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.Step;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Every move the Master makes: a verb it presses, or a step of the session's brief it relays. */
@RequiredArgsConstructor
public enum MasterMove {
    SHIP("ship", false),
    /** Posting the drafted replies on a ship the Master pressed. */
    REPLY("reply", false),
    DEPLOY("deploy", false),
    APPROVE_PLAN("approve-plan", false),
    ADD_REPO("add-repo", false),
    ROUND_NOT_READY("round-not-ready", true),
    PLAN_NOT_READY("plan-not-ready", true),
    ASKED_HUMAN("asked-human", true),
    PLAN_ANSWERED("plan-answered", true),
    ANSWERED("answered", true),
    ANSWERED_UNDER("answered-under", true);

    private final String id;
    private final boolean relayed;

    public String id() {
        return id;
    }

    public Step step() {
        return Step.of(id);
    }

    /** The verbs, held to {@code master-cases.md}; a step relayed is held to its session case alone. */
    public static Set<String> pressed() {
        return ids(false);
    }

    public static Set<String> relayed() {
        return ids(true);
    }

    private static Set<String> ids(boolean relayed) {
        return Arrays.stream(values()).filter(move -> move.relayed == relayed).map(MasterMove::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
