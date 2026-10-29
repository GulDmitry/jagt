package dev.jagt.orchestrator.port;

/** What the host says about the checks of one commit, in its own word, {@code none} where it ran none; {@code url} is blank then. */
public record CommitPipeline(String status, String url) {

    private static final String UNHOSTED = "unhosted";

    /** The repository has no code-host address, so no host could have a pipeline for it. */
    public static CommitPipeline unhosted() {
        return new CommitPipeline(UNHOSTED, "");
    }

    public boolean hosted() {
        return !UNHOSTED.equals(status);
    }
}
