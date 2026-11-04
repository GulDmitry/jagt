package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleInvalid;
import dev.jagt.orchestrator.flow.CheckerRole;
import dev.jagt.orchestrator.flow.Roles;
import dev.jagt.orchestrator.port.Problem;
import jakarta.annotation.PostConstruct;
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
 * The roles under {@code roles/<id>/} whose {@code role.yml} (the human's copy over the shipped {@code .dist}, else the {@code .dist})
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
    }

    @PostConstruct
    void install() {
        Roles.install(definitions.stream().map(InstalledRoles::checker).toList());
    }

    private static Optional<RoleDefinition> standingIn(Path folder, List<String> refused) {
        for (List<Path> layers : layerings(files(folder))) {
            String file = layers.getLast().getFileName().toString();
            try {
                RoleDefinition role = RoleDefinitions.fromFiles(layers);
                String why = refusal(folder.getFileName().toString(), role);
                if (why.isEmpty()) {
                    return Optional.of(role);
                }
                refused.add(file + ": " + why);
            } catch (RoleInvalid | org.yaml.snakeyaml.error.YAMLException invalid) {
                refused.add(file + ": " + invalid.getMessage());
            }
        }
        return Optional.empty();
    }

    private static List<List<Path>> layerings(List<Path> copyFirst) {
        return copyFirst.size() < 2 ? List.of(copyFirst)
                : List.of(copyFirst.reversed(), List.of(copyFirst.getLast()));
    }

    private static String refusal(String folder, RoleDefinition role) {
        return folder.equals(role.id()) ? Roles.lint(checker(role)).orElse("")
                : "its folder is " + folder + " but it names itself " + role.id();
    }

    private static CheckerRole checker(RoleDefinition role) {
        return new CheckerRole(role.id(), role.checking().jobs(), role.checking().readSteps());
    }

    public List<RoleDefinition> definitions() {
        return List.copyOf(definitions);
    }

    public List<Problem> skipped() {
        return List.copyOf(skipped);
    }

    public Optional<RoleDefinition> find(String id) {
        return definitions.stream().filter(role -> role.id().equals(id)).findFirst();
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
