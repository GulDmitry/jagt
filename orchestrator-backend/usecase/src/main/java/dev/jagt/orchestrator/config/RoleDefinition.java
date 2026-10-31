package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;

import java.util.List;
import java.util.Map;

/**
 * What makes one checker role the role it is: the id its jobs, records and region carry, its name to a human, and the
 * shapes of what it answers to a check and to a decline.
 */
public record RoleDefinition(String id, String name, Schema answer, Schema decline) {

    public static final RoleDefinition QA = new RoleDefinition("qa", "QA",
            Schema.answer()
                    .required("failure", "string", null)
                    .records("rows", null, List.of("criterion", "status", "evidence"),
                            Map.of("status", List.of("met", "not met", "unprovable")))
                    .text("question", null)
                    .text("waitingOn", null),
            Schema.answer()
                    .required("failure", "string", null)
                    .text("dispute", null));
}
