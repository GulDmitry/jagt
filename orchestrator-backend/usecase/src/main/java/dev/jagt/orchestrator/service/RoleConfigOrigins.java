package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Where each installed role's config value comes from: {@code jagt.yml}, the human's {@code role.yml}, the shipped {@code .dist}, or a default. */
@Slf4j
@Component
public final class RoleConfigOrigins {

    private static final String JAGT_YML = "jagt.yml";
    private static final String DEFAULT = "default";

    private final InstalledRoles roles;
    private final ConfigService config;
    private final OrchestratorPaths paths;

    public RoleConfigOrigins(InstalledRoles roles, ConfigService config, OrchestratorPaths paths) {
        this.roles = roles;
        this.config = config;
        this.paths = paths;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAtStart() {
        for (RoleDefinition role : roles.definitions()) {
            RoleConfig written = Files.exists(paths.configFile()) ? config.load().role(role.id()) : RoleConfig.defaults();
            report(role.id(), "enabled", written.enabledOrDefault(), written.enabled() == null ? DEFAULT : JAGT_YML);
            report(role.id(), "model", written.modelOrInherited().isEmpty() ? role.model() : written.modelOrInherited(),
                    modelOrigin(role, written));
            report(role.id(), "environments", written.environmentsOrEmpty().size(),
                    written.environments() == null ? DEFAULT : JAGT_YML);
        }
    }

    private static void report(String role, String key, Object value, String origin) {
        log.atInfo().setMessage("role config").addKeyValue("role", role).addKeyValue("key", key)
                .addKeyValue("value", value).addKeyValue("origin", origin).log();
    }

    private String modelOrigin(RoleDefinition role, RoleConfig written) {
        if (!written.modelOrInherited().isEmpty()) {
            return JAGT_YML;
        }
        return humanCopyNamesModel(role.id()) ? "role.yml" : "role.yml.dist";
    }

    private boolean humanCopyNamesModel(String id) {
        Path copy = paths.root().resolve("roles").resolve(id).resolve("role.yml");
        try {
            return Files.isRegularFile(copy) && new Yaml(new SafeConstructor(new LoaderOptions()))
                    .load(Files.readString(copy)) instanceof Map<?, ?> top && top.containsKey("model");
        } catch (IOException | RuntimeException unread) {
            return false;
        }
    }
}
