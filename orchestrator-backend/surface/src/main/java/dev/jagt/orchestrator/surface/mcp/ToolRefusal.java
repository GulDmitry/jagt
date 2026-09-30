package dev.jagt.orchestrator.surface.mcp;

/** A call jagt turned away before any work, knowing exactly why. */
public class ToolRefusal extends IllegalArgumentException {

    private final ToolFailure failure;

    public ToolRefusal(ToolFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public ToolFailure failure() {
        return failure;
    }
}
