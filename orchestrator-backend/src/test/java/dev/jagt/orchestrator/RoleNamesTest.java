package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import dev.jagt.orchestrator.flow.role.Arbiter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RoleNamesTest {

  private static final Map<String, String> ALLOWED =
      Map.of(
          "dev.jagt.orchestrator.port.worktree.AcceptanceRunner",
              "runs the flows a plan's acceptance criteria name, whichever role asks",
          "dev.jagt.orchestrator.service.scenario.AcceptanceProbing",
              "probes a plan's acceptance criteria, before any role checks the deploy",
          "dev.jagt.orchestrator.service.ConfigService$ConfigFile$MasterConfig",
              "the old orchestrator.master key, named as jagt.yml wrote it",
          "dev.jagt.orchestrator.flow.TaskMove$MasterNoted",
              "a move of the arbiter's region, in the chart's one closed set of moves");

  @Test
  void noClassOutsideRolesModuleNamesIt() {
    Map<String, List<String>> modules = new LinkedHashMap<>();
    modules.put("arbiter", RoleModules.ARBITER);
    RoleFolders.ids()
        .forEach(id ->
            modules.put(
                id.toLowerCase(Locale.ROOT),
                id.equals(Arbiter.ID)
                    ? RoleModules.ARBITER
                    : RoleModules.CHECKERS.getOrDefault(id, List.of())));

    assertThat(
            MainClasses.CODE.stream()
                .filter(type ->
                    modules.entrySet().stream()
                        .anyMatch(role ->
                            type.getSimpleName().toLowerCase(Locale.ROOT).startsWith(role.getKey())
                                && role.getValue().stream().noneMatch(type.getName()::startsWith)))
                .map(JavaClass::getName)
                .filter(name -> !ALLOWED.containsKey(name)))
        .isEmpty();
  }

  @Test
  void anAllowedNameIsClassThatStillExists() {
    assertThat(
            ALLOWED.keySet().stream()
                .filter(name ->
                    MainClasses.CODE.stream().noneMatch(type -> type.getName().equals(name))))
        .isEmpty();
  }

  @Test
  void everyRoleFolderHoldsDesign() {
    try (Stream<Path> folders = Files.list(Path.of("..", "roles"))) {
      assertThat(
              folders
                  .filter(Files::isDirectory)
                  .filter(folder ->
                      Files.exists(folder.resolve("brief.md.dist"))
                          || Files.exists(folder.resolve("role.yml.dist")))
                  .filter(folder -> !Files.exists(folder.resolve("design.md")))
                  .map(folder -> folder.getFileName().toString()))
          .isEmpty();
    } catch (IOException unread) {
      throw new UncheckedIOException(unread);
    }
  }
}
