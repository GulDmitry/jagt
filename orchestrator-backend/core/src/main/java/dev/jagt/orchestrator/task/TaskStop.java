package dev.jagt.orchestrator.task;

/** Why jagt stopped a task for the human, and when; only a human's lift clears it. */
public record TaskStop(Trigger trigger, long at, String evidence) {

    /** {@code next} is what a human reads after the evidence. */
    public enum Trigger {
        STALLED(Trigger.DEFECT), SHIPS(Trigger.DEFECT), MISS("Add a case if one should send it"),
        ROUNDS("Read what the session made of the last one"),
        CHECKS("Check that the code host's MCP can list the pipelines"),
        UNDELIVERED("Check that the session's window is running");

        private static final String DEFECT = "Likely a jagt defect: inspect the sweeps in the log";

        private final String next;

        Trigger(String next) {
            this.next = next;
        }
    }

    public String reading() {
        return evidence + ". " + trigger.next + ", then lift or close";
    }
}
