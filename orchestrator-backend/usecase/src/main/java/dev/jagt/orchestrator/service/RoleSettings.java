package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;

/** A role's definition with what {@code jagt.yml} says about it now. */
public record RoleSettings(RoleDefinition role, ConfigService config) {

    public RoleConfig load() {
        return config.load().role(role.id());
    }
}
