package dev.jagt.orchestrator.protocol;

import java.util.List;

/** One role's reading of a review round. */
public final class RoundRead {

    public static final List<String> VERDICTS = List.of("ready", "not ready", "question");

    public static final Schema SCHEMA = Schema.answer()
            .required("failure", "string", null)
            .choiceRequired("verdict", VERDICTS, null)
            .records("findings", null, List.of("file", "issue", "pattern"))
            .text("question", null)
            .records("premises", null, List.of("claim", "provenBy"));

    private RoundRead() {
    }
}
