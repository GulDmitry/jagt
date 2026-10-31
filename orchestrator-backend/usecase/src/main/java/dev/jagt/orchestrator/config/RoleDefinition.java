package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;

import java.util.List;

/**
 * What makes one checker role the role it is: the id its jobs, records and region carry, its name to a human, when
 * its check opens, which outcomes may end it, what its read may call, and the shapes of what it answers to a check
 * and to a decline.
 */
public record RoleDefinition(String id, String name, String model, List<String> trigger, String oncePer,
                             List<String> outcomes, Tools tools, Schema answer, Schema decline) {

    /** {@code cli} is the tool set the CLI offers, {@code allow} and {@code refuse} its permission lists. */
    public record Tools(List<String> cli, List<String> allow, List<String> refuse, boolean git, boolean fetch) {
    }
}
