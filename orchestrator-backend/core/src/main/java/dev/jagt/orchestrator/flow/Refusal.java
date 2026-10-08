package dev.jagt.orchestrator.flow;

/** A refusal a caller may need to ACT on, not merely show. */
public class Refusal extends IllegalArgumentException {

    public enum Code {
        /** The task is gone — whoever asked is looking at a view that no longer describes anything. */
        NO_SUCH_TASK,
        /** The task moved on since the view offering this action was rendered. */
        ACTION_NOT_AVAILABLE,
        /** What jagt already holds refuses it — a ceiling, a taken name, a status; resending will not pass. */
        STATE
    }

    private final Code code;

    public Refusal(Code code, String message) {
        super(message);
        this.code = code;
    }

    public static Refusal noSuchTask(String taskIdOrAlias) {
        return new Refusal(Code.NO_SUCH_TASK, "No task " + taskIdOrAlias + " — it may have been closed.");
    }

    public static Refusal byState(String message) {
        return new Refusal(Code.STATE, message);
    }

    public Code code() {
        return code;
    }
}
