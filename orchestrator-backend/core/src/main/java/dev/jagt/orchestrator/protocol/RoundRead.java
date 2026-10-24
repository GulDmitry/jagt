package dev.jagt.orchestrator.protocol;

import java.util.List;
import java.util.Map;

/** One role's reading of a review round. */
public final class RoundRead {

    public static final List<String> VERDICTS = List.of("ready", "not ready", "question");
    public static final List<String> SEVERITIES = List.of("blocking", "wrong", "unproven", "unguarded", "noise");
    public static final List<String> KINDS = List.of("behaviour", "missing", "scope", "test", "structure");

    public static final Schema SCHEMA = Schema.answer()
            .required("failure", "string", null)
            .choiceRequired("verdict", VERDICTS, null)
            .records("findings", null, List.of("file", "anchor", "kind", "issue", "pattern", "severity"),
                    Map.of("severity", SEVERITIES, "kind", KINDS))
            .text("question", null)
            .records("premises", null, List.of("claim", "provenBy"))
            .text("newTask", null);

    private RoundRead() {
    }
}
