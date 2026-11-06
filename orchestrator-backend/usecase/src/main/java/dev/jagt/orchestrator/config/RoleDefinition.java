package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;

import java.util.List;

/**
 * What makes one checker role the role it is: the id its jobs, records and region carry, its name to a human, when
 * its check opens, which outcomes may end it, what its read may call, and the shapes of what it answers to a check
 * and to a decline.
 */
public record RoleDefinition(String id, String name, String model, List<String> trigger, String oncePer,
                             List<String> outcomes, Tools tools, Schema answer, Schema decline,
                             Checking checking) {

    private static final Schema ABSENT = Schema.of("no role installed");

    /** The stand-in for a role no {@code role.yml} installs: it declares nothing, so its jobs find nothing to do. */
    public static RoleDefinition absent(String id) {
        return new RoleDefinition(id, id, "", List.of(), "", List.of(), new Tools(List.of(), List.of(), List.of(), false,
                false),
                ABSENT, ABSENT, new Checking(false, List.of()));
    }

    public boolean installed() {
        return answer != ABSENT;
    }

    /** Whether the role runs jobs, and the brief steps whose turn opens its read. */
    public record Checking(boolean jobs, List<String> readSteps) {
    }

    /** Command globs a read may run and may not, and path globs it may not open; reads are on, writes refused. */
    public record Tools(List<String> shell, List<String> refuseShell, List<String> refuseRead, boolean git,
                        boolean fetch) {
    }
}
