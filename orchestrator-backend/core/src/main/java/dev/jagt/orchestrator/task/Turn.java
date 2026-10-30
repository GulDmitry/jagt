package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Whose move a task waits on, since when, and the step that handed it over. {@code id} counts the turns, a relay or a
 * report starting the next; {@code marks} holds the last value each step carried and what the session had last
 * reported when it was relayed, one entry each per step, so it is bounded; {@code run} when {@code step} was relayed,
 * rounds running that answered one unchanged report.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Turn(String step, long id, String digest, long since,
                   @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> marks,
                   @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Long> run) {

    public static final int MAX_RUN = 20;

    private static final String ANSWERS = ":answers";

    public enum Holder {
        SESSION,
        MASTER,
        HUMAN,
        /** The code host: a pipeline or a reviewer. */
        CI,
        NOBODY
    }

    public Turn {
        step = step == null ? "" : step;
        digest = digest == null ? "" : digest;
        marks = marks == null ? Map.of() : Map.copyOf(marks);
        run = run == null ? List.of() : List.copyOf(run);
    }

    public static Turn none() {
        return new Turn("", 0, "", 0, Map.of(), List.of());
    }

    /** From a relay until the session's next report, which empties the digest. */
    @JsonIgnore
    public boolean open() {
        return !digest.isEmpty();
    }

    /** The same step with the same facts, while the session still holds the turn it opened. */
    public boolean holds(String step, String digest) {
        return open() && this.step.equals(step) && this.digest.equals(digest);
    }

    /** No relay opened and no report closed a turn between the two; marks aside. */
    public boolean sameTurnAs(Turn other) {
        return id == other.id && since == other.since;
    }

    public boolean marked(String step, String value) {
        return value.equals(marks.get(step));
    }

    public @Nullable String mark(String step) {
        return marks.get(step);
    }

    /** {@code answered} fingerprints the session's last report; a round answering another one starts the run afresh. */
    public Turn relayed(String step, String digest, String answered, long now) {
        Map<String, String> grown = new HashMap<>(marks);
        grown.put(step, digest);
        grown.put(step + ANSWERS, answered);
        return new Turn(step, id + 1, digest, now, grown, ran(step, answered, now));
    }

    /** A report closes the turn it answers and starts the next holder's; the step it answered stays on record. */
    public Turn closed(long now) {
        return new Turn(step, id + 1, "", now, marks, run);
    }

    public Turn withMark(String step, String value) {
        return new Turn(this.step, id, digest, since, with(step, value), run);
    }

    public Turn withoutMark(String step) {
        Map<String, String> kept = new HashMap<>(marks);
        kept.remove(step);
        return new Turn(this.step, id, digest, since, kept, run);
    }

    private List<Long> ran(String step, String answered, long now) {
        boolean continues = this.step.equals(step) && answered.equals(marks.get(step + ANSWERS));
        List<Long> grown = new ArrayList<>(continues ? run : List.of());
        grown.add(now);
        return grown.subList(Math.max(0, grown.size() - MAX_RUN), grown.size());
    }

    private Map<String, String> with(String step, String value) {
        Map<String, String> grown = new HashMap<>(marks);
        grown.put(step, value);
        return grown;
    }

    public static String digestOf(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
