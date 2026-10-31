package dev.jagt.orchestrator.config;

/** What makes one checker role the role it is: the id its jobs, records and region carry, and its name to a human. */
public record RoleDefinition(String id, String name) {

    public static final RoleDefinition QA = new RoleDefinition("qa", "QA");
}
