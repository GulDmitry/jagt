package dev.jagt.orchestrator.flow;

/**
 * The human-readable step of a task's life: {@link TaskStatus} is the persisted SSOT, but several of its values
 * all read as the one word "review" to a person. A phase is a PROJECTION for humans — never a second state
 * machine, never persisted.
 */
public enum Phase {

    /** The agent is writing code. */
    BUILD("build"),
    /** You read the diff — this happens BEFORE `ship`, and it is the checkpoint jagt exists to protect. */
    REVIEW("review"),
    /** The change is pushed; the pipeline and the reviewers have it. */
    CHECK("check"),
    /** Nothing left to address: it can go out. */
    READY("ready"),
    /** Merged into the deploy branch — or stuck in a conflict on the way there. */
    DEPLOY("deploy"),
    DONE("done");

    private final String label;

    Phase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
