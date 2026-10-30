package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** The rounds relayed into a task since {@code since}, the last lift. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoundTrail(List<Round> rounds, long since) {

    public static final int MAX_ROUNDS = 20;

    /** {@code failing} is the failing job's name, blank while the checks are not red; {@code notes} the round's note fingerprint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Round(long at, int threads, String failing, String digest, String notes) {

        public Round {
            notes = notes == null ? "" : notes;
        }

        public boolean repeats(String other) {
            return RoundKey.stored(notes).sameAs(RoundKey.stored(other));
        }
    }

    public RoundTrail {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }

    public static RoundTrail none() {
        return new RoundTrail(List.of(), 0);
    }

    public static RoundTrail liftedAt(long now) {
        return new RoundTrail(List.of(), now);
    }

    public RoundTrail withRound(Round round) {
        List<Round> grown = new ArrayList<>(rounds);
        grown.add(round);
        return new RoundTrail(grown.subList(Math.max(0, grown.size() - MAX_ROUNDS), grown.size()), since);
    }

    /** Open threads carrying the very notes of the last round, with the checks not red then nor now. */
    public boolean noNewNote(String notes, String failing) {
        return !rounds.isEmpty() && rounds.getLast().repeats(notes)
                && rounds.getLast().failing().isBlank() && failing.isBlank();
    }

    /** Known notes that the last round did not carry. */
    public boolean newNotes(String notes) {
        return RoundKey.stored(notes).known() && (rounds.isEmpty() || !rounds.getLast().repeats(notes));
    }

    public boolean relayed(String digest) {
        return !rounds.isEmpty() && rounds.getLast().digest().equals(digest);
    }
}
