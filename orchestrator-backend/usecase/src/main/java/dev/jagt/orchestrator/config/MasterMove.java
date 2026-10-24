package dev.jagt.orchestrator.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Every move the Master makes: a verb it presses, or a step of the session's brief it relays. */
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

    MasterMove(String id, boolean relayed) {
        this.id = id;
        this.relayed = relayed;
    }

    public String id() {
        return id;
    }

    public static Set<String> ids() {
        return Arrays.stream(values()).map(MasterMove::id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The move relaying {@code step}, empty where the Master relays no such step. */
    public static Optional<MasterMove> relaying(String step) {
        return Arrays.stream(values()).filter(move -> move.relayed && move.id.equals(step)).findFirst();
    }
}
