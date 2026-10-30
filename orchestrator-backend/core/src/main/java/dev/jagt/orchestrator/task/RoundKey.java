package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * What a round is the same by: the facts it was read from, in order. A fact that could not be read is unknown, and an
 * unknown is the same as nothing, itself included. There is no {@code equals}: sameness is {@link #sameAs} alone.
 */
public final class RoundKey {

    private static final RoundKey UNKNOWN = new RoundKey(List.of());

    private final List<String> facts;

    private RoundKey(List<String> facts) {
        this.facts = facts;
    }

    public static RoundKey unknown() {
        return UNKNOWN;
    }

    /** Unknown where there is no fact or any fact is null or blank. */
    public static RoundKey of(List<String> facts) {
        boolean unread = facts.isEmpty() || facts.stream().anyMatch(fact -> fact == null || fact.isBlank());
        return unread ? UNKNOWN : new RoundKey(List.copyOf(facts));
    }

    /** The key a {@link #stored} text names; a blank text is unknown. */
    public static RoundKey stored(String text) {
        return text == null || text.isBlank() ? UNKNOWN : new RoundKey(List.of(text));
    }

    public boolean known() {
        return this != UNKNOWN;
    }

    public boolean sameAs(RoundKey other) {
        return known() && other.known() && stored().equals(other.stored());
    }

    /** Blank where unknown. */
    public String stored() {
        return String.join("\n", facts);
    }
}
