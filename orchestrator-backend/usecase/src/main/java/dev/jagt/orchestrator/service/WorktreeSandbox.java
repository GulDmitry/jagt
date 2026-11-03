package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Sandbox;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.SandboxConfig;
import dev.jagt.orchestrator.task.NewRepo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class WorktreeSandbox {

    /** Where the build tools, a cluster client and a container client keep their state. */
    static final List<String> WRITABLE = List.of("/tmp", "~/.gradle", "~/.m2", "~/.npm", "~/.cache", "~/.kube",
            "~/.docker");

    /** A remote spoken to over ssh, which no web proxy carries. */
    static final List<String> EXCLUDED = List.of("git push *", "git fetch *", "git pull *", "git clone *",
            "git ls-remote *", "ssh *");

    private WorktreeSandbox() {
    }

    static Sandbox of(SandboxConfig config, List<NewRepo> repos, Path home, Path tempDir) {
        if (!config.enabledOrDefault()) {
            return Sandbox.off();
        }
        Set<Path> writable = new LinkedHashSet<>();
        repos.forEach(repo -> {
            writable.add(real(repo.worktreePath()));
            writable.add(real(repo.gitCommonDir()));
        });
        // A JVM opens its attach socket here, not in /tmp: denied, a test that mocks with an agent cannot start.
        writable.add(real(tempDir));
        Stream.concat(WRITABLE.stream().map(entry -> expanded(entry, home)), config.allowWritePaths(home).stream())
                .map(WorktreeSandbox::real)
                .forEach(writable::add);
        return Sandbox.on(List.copyOf(writable),
                Stream.concat(EXCLUDED.stream(), config.excludedCommandsOrEmpty().stream()).distinct().toList());
    }

    private static Path expanded(String entry, Path home) {
        return entry.startsWith("~/") ? home.resolve(entry.substring(2)) : Path.of(entry);
    }

    /** A sandbox matches the path a write resolves to, so a symlinked {@code /tmp} allows nothing. */
    static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
