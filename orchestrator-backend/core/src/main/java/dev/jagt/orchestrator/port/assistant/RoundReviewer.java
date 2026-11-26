package dev.jagt.orchestrator.port.assistant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * One reading of a review round by a fresh model, able to read and run in the task's worktrees and
 * to write nothing there. Metered, and asked once per role per round.
 */
public interface RoundReviewer {

  String QUESTION_LEAD = "The session working it stopped to ask:";

  /** How the code reviewer's read of a request opens. */
  String REVIEW_LEAD = "<role>You review request ";

  /** How the code reviewer's answer to a review thread opens. */
  String ANSWER_LEAD = "<role>You answer the newest note of one review thread on request ";

  /**
   * {@code shared} is what every reader of one round is given alike, apart from {@code prompt} so a
   * later reader finds it cached; blank where there is none. {@code worktrees} are read, the first
   * is where it runs. {@code model} blank inherits the agent CLI's own.
   */
  record Round(String shared, String prompt, List<Path> worktrees, String model) {

    /** The same round asked again, told what the last answer got wrong. */
    public Round correcting(List<String> corrections) {
      return corrections.isEmpty()
          ? this
          : new Round(
              shared,
              prompt
                  + "\n<correction>Your last answer was refused: "
                  + String.join("; ", corrections)
                  + "</correction>",
              worktrees,
              model);
    }

    public Round reaching(List<Path> beside) {
      return new Round(
          shared,
          prompt,
          Stream.concat(worktrees.stream(), beside.stream()).distinct().toList(),
          model);
    }
  }

  /**
   * {@code severity}: blocking, wrong, unproven, unguarded or noise; only the first three stop a
   * round. {@code file}, {@code anchor} and {@code kind} are what makes it the same finding in a
   * later round.
   */
  record Finding(
      String file, String issue, String pattern, String severity, String anchor, String kind) {

    public boolean stops() {
      return !is(Severity.UNGUARDED) && !is(Severity.NOISE);
    }

    public boolean is(Severity expected) {
      return expected.id().equals(severity);
    }

    /** The wire value of {@code severity}, in the order a reader ranks it. */
    public enum Severity {
      BLOCKING("blocking"),
      WRONG("wrong"),
      UNPROVEN("unproven"),
      UNGUARDED("unguarded"),
      NOISE("noise");

      private final String id;

      Severity(String id) {
        this.id = id;
      }

      public String id() {
        return id;
      }
    }
  }

  /**
   * {@code provenBy} is the command run and what it printed; blank where the claim was only
   * reasoned.
   */
  record Premise(String claim, String provenBy) {}

  /** {@code UNREAD} is a word that names none of the others. */
  enum Verdict {
    READY("ready"),
    NOT_READY("not ready"),
    QUESTION("question"),
    UNREAD("");

    private final String word;

    Verdict(String word) {
      this.word = word;
    }

    @JsonValue
    public String word() {
      return word;
    }

    @JsonCreator
    public static Verdict ofWord(String written) {
      String said = written.strip().toLowerCase(Locale.ROOT);
      return Arrays.stream(values())
          .filter(verdict -> verdict != UNREAD && verdict.word.equals(said))
          .findFirst()
          .orElse(UNREAD);
    }
  }

  /** What an answer to a question is: words, or the move whose id it names. */
  sealed interface Press {

    Press NONE = new Words();

    /** Blank where the answer is words. */
    @JsonValue
    String id();

    @JsonCreator
    static Press ofId(String written) {
      return written.isBlank() ? NONE : new Move(written.strip());
    }

    record Words() implements Press {

      @Override
      public String id() {
        return "";
      }
    }

    record Move(String id) implements Press {}
  }

  /**
   * {@code failure} non-blank means nothing else in it was read, {@code verdict} then being {@code
   * NOT_READY}. {@code newTask} is work the reader would open as a task of its own, blank where
   * none.
   */
  record Judgement(
      String failure,
      Verdict verdict,
      List<Finding> findings,
      String question,
      List<Premise> premises,
      String newTask,
      Press press) {

    public Judgement {
      press = press == null ? Press.NONE : press;
    }

    public static Judgement failed(String failure) {
      return new Judgement(failure, Verdict.NOT_READY, List.of(), "", List.of(), "", Press.NONE);
    }
  }

  Answer<Judgement> review(Round round);

  /** False where a read loads no MCP server, so no ticket the round does not quote can be read. */
  boolean loadsMcpServer();
}
