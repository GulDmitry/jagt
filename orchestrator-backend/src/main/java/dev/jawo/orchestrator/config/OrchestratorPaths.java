package dev.jawo.orchestrator.config;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the orchestrator root without any machine-specific configuration:
 * walks up from the launch directory until it finds mcp_client.js (a committed
 * root marker). Works both for `./gradlew bootRun` (started in
 * orchestrator-backend/) and for `java -jar` started from the root itself.
 * Everything is overridable via orchestrator.* properties / ORCHESTRATOR_ROOT.
 */
@Component
public class OrchestratorPaths {

    private static final String ROOT_MARKER = "mcp_client.js";

    private final Path root;
    private final Path configFile;
    private final Path stateFile;

    public OrchestratorPaths(OrchestratorProperties properties) {
        this.root = properties.root() != null && !properties.root().isBlank()
                ? Path.of(properties.root()).toAbsolutePath().normalize()
                : findRoot();
        this.configFile = resolve(properties.configFile(), "config.json");
        this.stateFile = resolve(properties.stateFile(), "state.json");
    }

    public Path root() {
        return root;
    }

    public Path configFile() {
        return configFile;
    }

    public Path stateFile() {
        return stateFile;
    }

    private Path resolve(String override, String defaultName) {
        return override != null && !override.isBlank()
                ? Path.of(override).toAbsolutePath().normalize()
                : root.resolve(defaultName);
    }

    private static Path findRoot() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(ROOT_MARKER))) {
                return dir;
            }
        }
        throw new IllegalStateException("Cannot locate orchestrator root: no " + ROOT_MARKER
                + " found in " + start + " or any parent. Set ORCHESTRATOR_ROOT.");
    }
}
