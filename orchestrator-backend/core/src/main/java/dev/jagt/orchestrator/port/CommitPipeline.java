package dev.jagt.orchestrator.port;

/**
 * What the code host says about the checks of one commit, in jagt's own five words: {@code success}, {@code failed},
 * {@code running}, {@code none} where it ran none, {@code unknown}. {@code url} is blank where none ran.
 */
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
