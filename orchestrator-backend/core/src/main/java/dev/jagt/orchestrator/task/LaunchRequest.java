package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A {@code do}, however it arrived — one record for every surface, so a modifier added to the grammar cannot
 * exist on one of them only.
 *
 * @param ref        an issue key or a URL to the item in any tracker
 * @param baseBranch branch to cut from and to target with the review request; null = the project's baseBranch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LaunchRequest(String ref, String project, String mode, String strategy, String baseBranch,
                            String notes) {

    public static LaunchRequest of(String ref) {
        return new LaunchRequest(ref, null, null, null, null, null);
    }

    /** Blanks become nulls: a surface that posts "" and one that omits the token must look the same. */
    public LaunchRequest normalized() {
        return new LaunchRequest(blankToNull(ref), blankToNull(project), blankToNull(mode),
                blankToNull(strategy), blankToNull(baseBranch), blankToNull(notes));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
