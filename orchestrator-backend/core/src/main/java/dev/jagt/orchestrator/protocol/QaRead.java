package dev.jagt.orchestrator.protocol;

import java.util.List;
import java.util.Map;

/** One QA run's reading of a deployed change, one row per acceptance criterion. */
public final class QaRead {

    public static final List<String> STATUSES = List.of("met", "not met", "unprovable");

    public static final Schema SCHEMA = Schema.answer()
            .required("failure", "string", null)
            .records("rows", null, List.of("criterion", "status", "evidence"), Map.of("status", STATUSES))
            .text("question", null)
            .text("waitingOn", null);

    public static final Schema DECLINE = Schema.answer()
            .required("failure", "string", null)
            .text("dispute", null);

    private QaRead() {
    }
}
