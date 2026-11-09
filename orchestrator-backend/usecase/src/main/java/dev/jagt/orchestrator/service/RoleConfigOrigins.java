package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Where each installed role's config value comes from: {@code jagt.yml}, the human's {@code
 * role.yml}, the shipped {@code .dist}, or a default.
 */
@Slf4j
@Component
public final class RoleConfigOrigins {

  private static final String ROLE_YML = "role.yml";
  private static final String DIST = "role.yml.dist";
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
      RoleConfig written =
          Files.exists(paths.configFile()) ? config.load().role(role.id()) : RoleConfig.defaults();
      Path folder = paths.root().resolve("roles").resolve(role.id());
      RoleOptions options = RoleOptions.read(folder);
      report(
          role.id(),
          "enabled",
          written.enabledOrDefault(),
          written.enabled() == null ? DEFAULT : "jagt.yml");
      report(
          role.id(),
          "model",
          options.model().isEmpty() ? role.model() : options.model(),
          names(folder.resolve(ROLE_YML), "model") ? ROLE_YML : DIST);
      report(
          role.id(),
          "environments",
          options.environments().size(),
          origin(folder, "environments"));
    }
  }

  private static void report(String role, String key, Object value, String origin) {
    log.atInfo()
        .setMessage("role config")
        .addKeyValue("role", role)
        .addKeyValue("key", key)
        .addKeyValue("value", value)
        .addKeyValue("origin", origin)
        .log();
  }

  private static String origin(Path folder, String key) {
    if (names(folder.resolve(ROLE_YML), key)) {
      return ROLE_YML;
    }
    return names(folder.resolve(DIST), key) ? DIST : DEFAULT;
  }

  private static boolean names(Path file, String key) {
    try {
      return Files.isRegularFile(file)
          && new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file))
              instanceof Map<?, ?> top
          && top.containsKey(key);
    } catch (IOException | RuntimeException unread) {
      return false;
    }
  }
}
