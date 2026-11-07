package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * One acceptance check a ratified plan declared, which jagt runs itself. {@code passed} is null
 * until it ran.
 *
 * @param repo the task repository a command runs in; null for the primary one
 * @param words the command's argv, or the one ref or path
 * @param exit the exit a command must end with; 0 for the others
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Probe(
    Kind kind,
    @Nullable String repo,
    List<String> words,
    int exit,
    @Nullable Boolean passed,
    @Nullable String excerpt) {

  public enum Kind {
    COMMAND,
    REF,
    ARTIFACT
  }

  public Probe {
    words = List.copyOf(words);
  }

  public static Probe specified(Kind kind, List<String> words, int exit) {
    return specified(kind, null, words, exit);
  }

  public static Probe specified(Kind kind, @Nullable String repo, List<String> words, int exit) {
    return new Probe(kind, repo, words, exit, null, null);
  }

  public Probe probed(boolean passed, String excerpt) {
    return new Probe(kind, repo, words, exit, passed, excerpt);
  }

  @JsonIgnore
  public boolean failed() {
    return Boolean.FALSE.equals(passed);
  }

  /** As a plan writes it. */
  @Override
  public String toString() {
    return kind.name().toLowerCase(Locale.ROOT)
        + (repo == null ? "" : " repo=" + repo)
        + (exit == 0 ? "" : " exit=" + exit)
        + " "
        + String.join(" ", words);
  }

  /** The probe, its verdict, and what it printed. */
  @JsonIgnore
  public String result() {
    String verdict = passed == null ? "not run" : passed ? "passed" : "FAILED";
    return this + " — " + verdict + (excerpt == null || excerpt.isBlank() ? "" : "\n" + excerpt);
  }
}
