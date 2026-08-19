package dev.jagt.orchestrator.config;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the orchestrator root without any machine-specific configuration: walks up from the launch
 * directory until it finds a committed root marker. Works both for `./gradlew bootRun` (started in
 * orchestrator-backend/) and for `java -jar` started from the root itself. Everything is overridable via
 * orchestrator.* properties / ORCHESTRATOR_ROOT.
 *
 * <p>TWO markers are accepted, and that is deliberate: `mcp_client.js` is only still here for agents that
 * cannot talk to a remote MCP server, so "where is the root" must not depend on whether that bridge exists.
 */
@Component
public class OrchestratorPaths {

    private static final java.util.List<String> ROOT_MARKERS =
            java.util.List.of("config.json.dist", "mcp_client.js");

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
            for (String marker : ROOT_MARKERS) {
                if (Files.exists(dir.resolve(marker))) {
                    return dir;
                }
            }
        }
        throw new IllegalStateException("Cannot locate orchestrator root: none of " + ROOT_MARKERS
                + " found in " + start + " or any parent. Set ORCHESTRATOR_ROOT.");
    }
}
