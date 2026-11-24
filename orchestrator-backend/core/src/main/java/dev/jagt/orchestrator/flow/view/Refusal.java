package dev.jagt.orchestrator.flow.view;

/** A refusal a caller may need to ACT on, not merely show. */
public class Refusal extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  public enum Code {
    /** The task is gone — whoever asked is looking at a view that no longer describes anything. */
    NO_SUCH_TASK,
    /** The task moved on since the view offering this action was rendered. */
    ACTION_NOT_AVAILABLE,
    /**
     * What jagt already holds refuses it — a ceiling, a taken name, a status; resending will not
     * pass.
     */
    STATE,
    /**
     * A start named a branch that a previous run left; the caller may choose what to do with it.
     */
    BRANCH_EXISTS
  }

  private final Code code;

  public Refusal(Code code, String message) {
    super(message);
    this.code = code;
  }

  public static Refusal noSuchTask(String taskIdOrAlias) {
    return new Refusal(
        Code.NO_SUCH_TASK, "No task " + taskIdOrAlias + " — it may have been closed.");
  }

  public static Refusal byState(String message) {
    return new Refusal(Code.STATE, message);
  }

  public Code code() {
    return code;
  }
}
