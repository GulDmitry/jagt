package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            return noteIds(notes).sameAs(noteIds(other));
        }
    }

    /** The host's note ids alone: a thread's first line is model-written and reads differently from one read to the next. */
    private static RoundKey noteIds(String notes) {
        return RoundKey.stored(notes).known()
                ? RoundKey.of(notes.lines().map(line -> line.substring(line.lastIndexOf(' ') + 1)).distinct().sorted().toList())
                : RoundKey.unknown();
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

    /** Each open thread told as new, carrying a new note, or unchanged since the last round; blank before a second round. */
    public String sinceLast(ReviewFacts facts) {
        if (rounds.isEmpty() || facts.newestNotes().size() != facts.threads().size()) {
            return "";
        }
        Map<String, String> last = newestNoteByThread(rounds.getLast().notes());
        Map<String, List<String>> told = new LinkedHashMap<>();
        for (int i = 0; i < facts.threads().size(); i++) {
            String thread = facts.threads().get(i).strip().lines().findFirst().orElse("");
            String seen = last.get(thread);
            String kind = seen == null ? "new" : seen.equals(facts.newestNotes().get(i)) ? "still open, unchanged"
                    : "still open, with a new note";
            told.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(thread);
        }
        List<String> groups = told.entrySet().stream()
                .map(group -> group.getKey() + ": " + String.join(", ", group.getValue())).toList();
        return "Since the last round — " + String.join("; ", groups) + ".";
    }

    private static Map<String, String> newestNoteByThread(String notes) {
        Map<String, String> byThread = new HashMap<>();
        for (String line : notes.lines().toList()) {
            int space = line.lastIndexOf(' ');
            if (space > 0) {
                byThread.put(line.substring(0, space), line.substring(space + 1));
            }
        }
        return byThread;
    }

    public boolean relayed(String digest) {
        return !rounds.isEmpty() && rounds.getLast().digest().equals(digest);
    }
}
