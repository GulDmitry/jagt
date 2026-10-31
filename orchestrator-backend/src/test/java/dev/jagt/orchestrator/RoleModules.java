package dev.jagt.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class RoleModules {

    private static final String ROOT = "dev.jagt.orchestrator.";

    static final List<String> ARBITER = Stream.of("service.master.", "flow.Master", "flow.Arbiter", "task.Master",
            "task.LegacyMaster", "job.Master", "command.Master", "config.Master", "startup.Master",
            "surface.mcp.MasterToken", "eval.Master").map(prefix -> ROOT + prefix).toList();

    static final Map<String, List<String>> CHECKERS = Map.of("qa",
            Stream.of("eval.Qa", "startup.Qa", "task.Qa").map(prefix -> ROOT + prefix).toList());

    private RoleModules() {
    }
}
