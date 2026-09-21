package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.TicketFacts;

import java.util.ArrayList;
import java.util.List;

/**
 * What an answer about a work item has to hold. The reader is a model, so an answer in the shape of a schema and
 * empty of facts is the usual failure: these are the rules that tell the two apart and say what to fix.
 */
public final class TicketRead {

    private TicketRead() {
    }

    /** Empty means the answer can be acted on. {@code ref} is what was asked for, so a swapped key is caught. */
    public static List<Violation> violations(String ref, TicketFacts facts) {
        List<Violation> found = new ArrayList<>();
        if (!facts.exists()) {
            return List.of();
        }
        if (blank(facts.key())) {
            found.add(new Violation("key", "the item's own key, read back from it, never parsed out of a URL"));
        }
        if (blank(facts.title())) {
            found.add(new Violation("title",
                    "the item's summary, or at most eight words written from its description"));
        }
        if (blank(facts.url())) {
            found.add(new Violation("url", "the item's canonical web link; never invent one"));
        } else if (!facts.url().startsWith("http://") && !facts.url().startsWith("https://")) {
            found.add(new Violation("url", "an http(s) link, not " + facts.url()));
        }
        if (answersForAnotherItem(ref, facts.key())) {
            found.add(new Violation("key", "the item asked for, which was " + ref + ", not " + facts.key()));
        }
        return List.copyOf(found);
    }

    /** Only a bare key can be compared: a URL names its item in a shape no two trackers share. */
    private static boolean answersForAnotherItem(String ref, String key) {
        return ref != null && !ref.contains("://") && !blank(key) && !ref.equalsIgnoreCase(key.strip());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
