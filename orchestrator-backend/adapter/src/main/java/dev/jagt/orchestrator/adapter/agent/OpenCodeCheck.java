package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.InstalledRoles;
import dev.jagt.orchestrator.startup.HttpAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Wherever OpenCode runs, its binary is on PATH and its endpoint is an http(s) address; a role read there names an
 * installed role, an endpoint and a model, since a read must never fall to the user's own provider.
 */
@Component
public class OpenCodeCheck implements StartupCheck {

    private static final String ENDPOINT_KEY = "orchestrator.opencode.endpoint";
    private static final String MODEL_KEY = "orchestrator.opencode.model";
    private static final String READS_KEY = "orchestrator.opencode.reads";
    private static final String MASTER = "master";

    private final OpenCodeProperties opencode;
    private final InstalledRoles roles;
    private final boolean sessions;

    public OpenCodeCheck(OpenCodeProperties opencode, InstalledRoles roles,
                         @Value("${orchestrator.agent.cli:claude}") String cli) {
        this.opencode = opencode;
        this.roles = roles;
        this.sessions = "opencode".equals(cli);
    }

    @Override
    public List<Problem> problems() {
        if (!sessions && opencode.reads().isEmpty()) {
            return List.of();
        }
        List<Problem> problems = new ArrayList<>();
        if (Executables.unresolved(Executables.resolve(opencode.command()))) {
            problems.add(new Problem("orchestrator.opencode.command",
                    "orchestrator.opencode.command: '" + opencode.command() + "' is not on PATH"));
        }
        problems.addAll(endpointProblems());
        problems.addAll(readProblems());
        return problems;
    }

    private List<Problem> endpointProblems() {
        List<Problem> problems = new ArrayList<>();
        if (opencode.hasEndpoint() && !HttpAddress.is(opencode.endpoint())) {
            problems.add(new Problem(ENDPOINT_KEY, ENDPOINT_KEY + ": '" + opencode.endpoint()
                    + "' is not an http(s) address"));
        }
        if (sessions && opencode.hasEndpoint() && opencode.model().isEmpty()) {
            problems.add(new Problem(MODEL_KEY, MODEL_KEY + ": an endpoint needs the model name to send"));
        }
        return problems;
    }

    private List<Problem> readProblems() {
        List<Problem> problems = new ArrayList<>();
        if (!opencode.reads().isEmpty() && !opencode.hasEndpoint()) {
            problems.add(new Problem(ENDPOINT_KEY, ENDPOINT_KEY + ": " + READS_KEY + " needs the endpoint its reads go to"));
        }
        List<String> installed = roles.definitions().stream().map(RoleDefinition::id).toList();
        for (String role : opencode.reads().keySet()) {
            if (!MASTER.equals(role) && !installed.contains(role)) {
                problems.add(new Problem(READS_KEY, READS_KEY + ": '" + role + "' is neither master nor an installed role"));
            } else if (opencode.readModel(role).orElse("").isEmpty()) {
                problems.add(new Problem(READS_KEY, READS_KEY + ": '" + role + "' has no model, nor has " + MODEL_KEY));
            }
        }
        return problems;
    }
}
