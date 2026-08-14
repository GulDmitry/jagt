package dev.jagt.orchestrator.service;

/**
 * A refusal a caller may need to ACT on, not merely show. The sentence stays the whole answer for a human; the
 * code exists only where something branches on it, so a reason nobody handles differently keeps throwing plain.
 */
public class Refusal extends IllegalArgumentException {

    public enum Code {
        /** The task is gone — whoever asked is looking at a view that no longer describes anything. */
        NO_SUCH_TASK,
        /** The task moved on since the view offering this action was rendered. */
        ACTION_NOT_AVAILABLE
    }

    private final Code code;

    public Refusal(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
