package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;

import java.util.List;

/**
 * What makes one checker role the role it is: the id its jobs, records and region carry, its name to a human, when
 * its check opens, which outcomes may end it, what its read may call, and the shapes of what it answers to a check
 * and to a decline.
 */
public record RoleDefinition(String id, String name, String model, Trigger trigger, Replies replies, Tools tools,
                             Checking checking) {

    private static final Schema ABSENT = Schema.of("no role installed");

    /** The stand-in for a role no {@code role.yml} installs: it declares nothing, so its jobs find nothing to do. */
    public static RoleDefinition absent(String id) {
        return new RoleDefinition(id, id, "", new Trigger(List.of(), ""), new Replies(List.of(), ABSENT, ABSENT),
                new Tools(List.of(), List.of(), List.of(), false, false), new Checking(false, List.of()));
    }

    public boolean installed() {
        return replies.answer() != ABSENT;
    }

    /** The events whose coincidence opens a check, and the unit of work it opens once per. */
    public record Trigger(List<String> trigger, String oncePer) {
    }

    /** The outcomes that may end a read, and the shapes of what it answers to a check and to a decline. */
    public record Replies(List<String> outcomes, Schema answer, Schema decline) {
    }

    /** Whether the role runs jobs, and the brief steps whose turn opens its read. */
    public record Checking(boolean jobs, List<String> readSteps) {
    }

    /** Command globs a read may run and may not, and path globs it may not open; reads are on, writes refused. */
    public record Tools(List<String> shell, List<String> refuseShell, List<String> refuseRead, boolean git,
                        boolean fetch) {
    }
}
