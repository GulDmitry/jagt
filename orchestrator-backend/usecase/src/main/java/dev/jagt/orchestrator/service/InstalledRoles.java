package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleInvalid;
import dev.jagt.orchestrator.flow.Roles;
import dev.jagt.orchestrator.port.Problem;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;

/**
 * The roles under {@code roles/<id>/} whose {@code role.yml} (the human's copy, else the shipped {@code .dist})
 * compiles and composes into the task chart, installed as the chart's roles; one that does not is skipped with its reason.
 */
@Component
public final class InstalledRoles {

    private final List<RoleDefinition> definitions = new ArrayList<>();
    private final List<Problem> skipped = new ArrayList<>();

    public InstalledRoles(OrchestratorPaths paths) {
        for (Path folder : folders(paths.root().resolve("roles"))) {
            List<String> refused = new ArrayList<>();
            Optional<RoleDefinition> standing = standingIn(folder, refused);
            standing.ifPresent(definitions::add);
            if (!refused.isEmpty()) {
                skipped.add(new Problem("roles/" + folder.getFileName(), String.join("; ", refused)
                        + (standing.isPresent() ? "; the shipped file stands" : "")));
            }
        }
        Roles.install(definitions.stream().map(RoleDefinition::id).toList());
    }

    private static Optional<RoleDefinition> standingIn(Path folder, List<String> refused) {
        for (Path file : files(folder)) {
            try {
                RoleDefinition role = RoleDefinitions.fromFile(file);
                String why = refusal(folder.getFileName().toString(), role);
                if (why.isEmpty()) {
                    return Optional.of(role);
                }
                refused.add(file.getFileName() + ": " + why);
            } catch (RoleInvalid | org.yaml.snakeyaml.error.YAMLException invalid) {
                refused.add(file.getFileName() + ": " + invalid.getMessage());
            }
        }
        return Optional.empty();
    }

    private static String refusal(String folder, RoleDefinition role) {
        return folder.equals(role.id()) ? Roles.lint(role.id()).orElse("")
                : "its folder is " + folder + " but it names itself " + role.id();
    }

    public List<RoleDefinition> definitions() {
        return List.copyOf(definitions);
    }

    public List<Problem> skipped() {
        return List.copyOf(skipped);
    }

    public RoleDefinition require(String id) {
        return definitions.stream().filter(role -> role.id().equals(id)).findFirst().orElseThrow(
                () -> new IllegalStateException("role " + id + " is not installed: " + skipped));
    }

    private static List<Path> files(Path folder) {
        return Stream.of("role.yml", "role.yml.dist").map(folder::resolve).filter(Files::exists).toList();
    }

    private static List<Path> folders(Path roles) {
        if (!Files.isDirectory(roles)) {
            return List.of();
        }
        try (Stream<Path> found = Files.list(roles)) {
            return found.filter(Files::isDirectory)
                    .filter(folder -> !files(folder).isEmpty()).sorted().toList();
        } catch (IOException unread) {
            throw new UncheckedIOException(unread);
        }
    }
}
