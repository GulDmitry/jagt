package dev.jagt.orchestrator.task;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A step a human may keep for themselves even where the Master acts for them. Named rather than inferred
 * from the verbs, because one of them — answering a reviewer — is a step inside a verb rather than a verb.
 */
public enum MasterRight {

    /** Opening or updating a review request. */
    SHIP,
    /** Reading a round back off the code host. */
    SWEEP,
    /** Merging into the deploy branch. */
    DEPLOY,
    /** Taking a deploy back out. */
    REVERT,
    /** Posting a drafted reply to a person on the request. */
    REPLY,
    /** Deciding what the ticket or the code leaves open, as the human would. */
    ANSWER,
    /** Telling a session its plan holds and to start on it. */
    PLAN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(MasterRight::id).toList();
    }

    /** Empty where the word is not one of these, so a misspelled step is refused rather than ignored. */
    public static Optional<MasterRight> of(String right) {
        return right == null ? Optional.empty()
                : Arrays.stream(values()).filter(value -> value.id().equals(right.strip().toLowerCase(Locale.ROOT)))
                        .findFirst();
    }
}
