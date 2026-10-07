package dev.jagt.orchestrator.protocol;

import java.util.Collection;

/**
 * Which repository an item's work belongs in, asked only where the labels answered nothing or answered twice.
 * The answer is CONSTRAINED to the projects an install configured plus {@code none}: a model naming a repository
 * jagt does not have is the failure this shape makes impossible.
 */
public final class ProjectRead {

    /** The answer for "I cannot tell", which is a human's to settle rather than a guess to take. */
    public static final String NONE = "none";

    private ProjectRead() {
    }

    public static Schema schemaFor(Collection<String> projectKeys) {
        return Schema.answer()
                .required("failure", "string", null)
                .choiceRequired("project", Wire.withNone(projectKeys, NONE), null)
                .required("reason", "string", null)
                .required("rule", "string", null);
    }
}
