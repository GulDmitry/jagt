package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The rounds relayed into a task since {@code since}, the last lift. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoundTrail(List<Round> rounds, long since) {

  public static final int MAX_ROUNDS = 20;

  /**
   * {@code failing} is the failing job's name, blank while the checks are not red; {@code notes}
   * the round's note fingerprint.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Round(long at, int threads, String failing, String digest, String notes) {

    public Round {
      notes = notes == null ? "" : notes;
    }

    public boolean repeats(String other) {
      return noteIds(notes).sameAs(noteIds(other));
    }
  }

  /** The host's note ids alone: a stored line may still lead with the thread it was read on. */
  private static RoundKey noteIds(String notes) {
    return RoundKey.stored(notes).known()
        ? RoundKey.of(
            notes
                .lines()
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .distinct()
                .sorted()
                .toList())
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
    return new RoundTrail(
        grown.subList(Math.max(0, grown.size() - MAX_ROUNDS), grown.size()), since);
  }

  /**
   * Open threads carrying the very notes of the last round, with the checks not red then nor now.
   */
  public boolean noNewNote(String notes, String failing) {
    return !rounds.isEmpty()
        && rounds.getLast().repeats(notes)
        && rounds.getLast().failing().isBlank()
        && failing.isBlank();
  }

  /** Known notes that the last round did not carry. */
  public boolean newNotes(String notes) {
    return RoundKey.stored(notes).known() && (rounds.isEmpty() || !rounds.getLast().repeats(notes));
  }

  /**
   * The notes told as new or unchanged against the last round's; blank before a second round or
   * where no note id is known.
   */
  public String sinceLast(ReviewFacts facts) {
    if (rounds.isEmpty() || facts.discussion().newestNotes().isEmpty()) {
      return "";
    }
    Set<String> last = new HashSet<>(noteIds(rounds.getLast().notes()).facts());
    long fresh = facts.discussion().newestNotes().stream().filter(id -> !last.contains(id)).count();
    long kept = facts.discussion().newestNotes().size() - fresh;
    List<String> told = new ArrayList<>();
    if (fresh > 0) {
      told.add("new notes: " + fresh);
    }
    if (kept > 0) {
      told.add("still open, unchanged: " + kept);
    }
    return "Since the last round — " + String.join("; ", told) + ".";
  }

  public boolean relayed(String digest) {
    return !rounds.isEmpty() && rounds.getLast().digest().equals(digest);
  }
}
