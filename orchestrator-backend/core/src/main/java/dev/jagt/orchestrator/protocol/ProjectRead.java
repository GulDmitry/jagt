package dev.jagt.orchestrator.protocol;

import java.util.ArrayList;
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
                .choiceRequired("project", withNone(projectKeys), null)
                .required("reason", "string", null)
                .required("rule", "string", null);
    }

    private static Collection<String> withNone(Collection<String> projectKeys) {
        var allowed = new ArrayList<String>(projectKeys);
        allowed.add(NONE);
        return allowed;
    }
}
