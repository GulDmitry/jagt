package dev.jagt.orchestrator.stand;

public class StandFailure extends RuntimeException {

    public StandFailure(String cause) {
        super(cause);
    }

    public StandFailure(String cause, Throwable underlying) {
        super(cause, underlying);
    }
}
