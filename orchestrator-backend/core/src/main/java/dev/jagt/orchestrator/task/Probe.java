package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * One acceptance check a ratified plan declared, which jagt runs itself. {@code passed} is null until it ran.
 *
 * @param words the command's argv, or the one ref or path
 * @param exit  the exit a command must end with; 0 for the others
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Probe(Kind kind, List<String> words, int exit, @Nullable Boolean passed, @Nullable String excerpt) {

    public enum Kind {
        COMMAND,
        REF,
        ARTIFACT
    }

    public Probe {
        words = List.copyOf(words);
    }

    public static Probe specified(Kind kind, List<String> words, int exit) {
        return new Probe(kind, words, exit, null, null);
    }

    public Probe probed(boolean passed, String excerpt) {
        return new Probe(kind, words, exit, passed, excerpt);
    }

    @JsonIgnore
    public boolean failed() {
        return Boolean.FALSE.equals(passed);
    }

    /** As a plan writes it. */
    @Override
    public String toString() {
        return kind.name().toLowerCase(Locale.ROOT) + (exit == 0 ? "" : " exit=" + exit) + " " + String.join(" ", words);
    }

    /** The probe, its verdict, and what it printed. */
    @JsonIgnore
    public String result() {
        String verdict = passed == null ? "not run" : passed ? "passed" : "FAILED";
        return this + " — " + verdict + (excerpt == null || excerpt.isBlank() ? "" : "\n" + excerpt);
    }
}
