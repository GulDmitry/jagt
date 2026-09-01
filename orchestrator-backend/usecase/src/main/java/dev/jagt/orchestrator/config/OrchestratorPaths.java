package dev.jagt.orchestrator.config;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the orchestrator root by walking up from the launch directory to a committed marker. TWO are accepted,
 * so the root does not depend on whether the stdio bridge file is still there.
 */
@Component
public class OrchestratorPaths {

    private static final java.util.List<String> ROOT_MARKERS =
            java.util.List.of("jagt.yml.dist", "mcp_client.js");

    private final Path root;
    private final Path configFile;
    private final Path stateFile;

    public OrchestratorPaths(OrchestratorProperties properties) {
        this.root = properties.root() != null && !properties.root().isBlank()
                ? Path.of(properties.root()).toAbsolutePath().normalize()
                : findRoot();
        this.configFile = resolve(properties.configFile(), "jagt.yml");
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

    /** Answered WITHOUT a Spring context: a launch has to hand Spring that file before there is one. */
    public static Path configFileOutside(String[] args) {
        String named = named(args, "--orchestrator.config-file=");
        String env = System.getenv("ORCHESTRATOR_CONFIG_FILE");
        String override = named != null ? named : env;
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        String rootOverride = named(args, "--orchestrator.root=");
        String rootEnv = System.getenv("ORCHESTRATOR_ROOT");
        String root = rootOverride != null ? rootOverride : rootEnv;
        return (root != null && !root.isBlank()
                ? Path.of(root).toAbsolutePath().normalize()
                : findRoot()).resolve("jagt.yml");
    }

    private static String named(String[] args, String flag) {
        for (String arg : args) {
            if (arg.startsWith(flag)) {
                return arg.substring(flag.length());
            }
        }
        return null;
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
