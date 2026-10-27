package dev.jagt.orchestrator.eval;

public class EvalStalled extends RuntimeException {

    private EvalStalled(String message) {
        super(message);
    }

    public static EvalStalled because(String message) {
        return new EvalStalled(message);
    }
}
