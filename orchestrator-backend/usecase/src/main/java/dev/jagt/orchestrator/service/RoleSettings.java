package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.QaConfig;

/** A role's definition with what {@code jagt.yml} says about it now. */
public record RoleSettings(RoleDefinition role, ConfigService config) {

    public QaConfig load() {
        return config.load().qa();
    }
}
