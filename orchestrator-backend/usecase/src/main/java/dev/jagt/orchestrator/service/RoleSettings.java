package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;

/** A role's definition under what {@code jagt.yml} says about it now; a role not installed is always off. */
public record RoleSettings(RoleDefinition role, ConfigService config) {

    public RoleConfig load() {
        RoleConfig written = role.installed() ? config.load().role(role.id()) : new RoleConfig(false, null, null);
        return written.modelOrInherited().isEmpty() ? written.withModel(role.model()) : written;
    }
}
