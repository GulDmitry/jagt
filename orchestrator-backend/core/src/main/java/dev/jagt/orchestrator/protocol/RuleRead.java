package dev.jagt.orchestrator.protocol;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Which written rule a human's own placement has just contradicted. Constrained to the rules the install holds
 * plus {@code none}: a model inventing a rule to retire would retire nothing, silently.
 */
public final class RuleRead {

    /** The answer for "none of them", which is the usual one and must stay cheaper than guessing. */
    public static final String NONE = "none";

    private RuleRead() {
    }

    public static Schema schemaFor(Collection<String> rules) {
        return Schema.answer()
                .required("failure", "string", null)
                .choiceRequired("rule", withNone(rules), null)
                .required("reason", "string", null);
    }

    private static Collection<String> withNone(Collection<String> rules) {
        var allowed = new ArrayList<String>(rules);
        allowed.add(NONE);
        return allowed;
    }
}
