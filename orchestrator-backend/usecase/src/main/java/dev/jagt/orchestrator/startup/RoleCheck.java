package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.InstalledRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** A role that did not compose is left out and said so; it stops the start only where jagt.yml turns it on. */
@Component
@RequiredArgsConstructor
public class RoleCheck implements StartupCheck {

    private static final String QA_KEY = "roles/" + Checkers.QA;

    private final InstalledRoles roles;
    private final ConfigService config;
    private final OrchestratorPaths paths;

    @Override
    public List<Problem> problems() {
        return qaMissing() && qaEnabled() ? List.of(qaWhy()) : List.of();
    }

    @Override
    public List<Problem> notes() {
        List<Problem> notes = new ArrayList<>(roles.skipped());
        if (qaMissing() && roles.skipped().stream().noneMatch(skipped -> skipped.key().equals(QA_KEY))) {
            notes.add(qaWhy());
        }
        notes.addAll(unknownKeys());
        return notes;
    }

    @Override
    public boolean machine() {
        return false;
    }

    private boolean qaMissing() {
        return roles.find(Checkers.QA).isEmpty();
    }

    private boolean qaEnabled() {
        return Files.exists(paths.configFile()) && config.load().role(Checkers.QA).enabledOrDefault();
    }

    private List<Problem> unknownKeys() {
        if (!Files.exists(paths.configFile())) {
            return List.of();
        }
        List<String> installed = roles.definitions().stream().map(RoleDefinition::id).toList();
        return config.load().unknownRoles(installed).stream().map(id -> new Problem("roles." + id,
                "orchestrator.roles." + id + " names no installed role; the installed ones are " + installed)).toList();
    }

    private Problem qaWhy() {
        return roles.skipped().stream().filter(skipped -> skipped.key().equals(QA_KEY)).findFirst()
                .orElse(new Problem(QA_KEY, "no roles/qa/role.yml or role.yml.dist is installed, so QA runs nothing"));
    }
}
