package technical.maintainability.modularity;

import com.tngtech.archunit.core.domain.JavaClass;
import dev.jagt.orchestrator.MainClasses;
import dev.jagt.orchestrator.RoleFolders;
import dev.jagt.orchestrator.RoleModules;
import dev.jagt.orchestrator.flow.role.Arbiter;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class RoleNamesFitness implements Fitness {

  private static final Map<String, String> ALLOWED =
      Map.of(
          "dev.jagt.orchestrator.service.ConfigService$ConfigFile$ArbiterConfig",
              "the old orchestrator.master key, named as jagt.yml wrote it",
          "dev.jagt.orchestrator.flow.TaskMove$MasterNoted",
              "a move of the arbiter's region, in the chart's one closed set of moves");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.of(
                classesOutsideTheirModule(),
                allowedNamesGone(),
                foldersWithoutDesign(),
                declaredModulesGone())
            .flatMap(items -> items)
            .toList());
  }

  private static Stream<String> classesOutsideTheirModule() {
    Map<String, List<String>> modules = new LinkedHashMap<>();
    modules.put("arbiter", RoleModules.ARBITER);
    RoleFolders.ids()
        .forEach(id ->
            modules.put(
                id.toLowerCase(Locale.ROOT),
                id.equals(Arbiter.ID)
                    ? RoleModules.ARBITER
                    : RoleModules.CHECKERS.getOrDefault(id, List.of())));
    return MainClasses.CODE.stream()
        .filter(type ->
            modules.entrySet().stream()
                .anyMatch(role ->
                    type.getSimpleName().toLowerCase(Locale.ROOT).startsWith(role.getKey())
                        && role.getValue().stream().noneMatch(type.getName()::startsWith)))
        .map(JavaClass::getName)
        .filter(name -> !ALLOWED.containsKey(name))
        .map(name -> "names a role outside its modules: " + name);
  }

  private static Stream<String> allowedNamesGone() {
    return ALLOWED.keySet().stream()
        .filter(name -> MainClasses.CODE.stream().noneMatch(type -> type.getName().equals(name)))
        .map(name -> "an allowed name is a class that is gone: " + name);
  }

  private static Stream<String> foldersWithoutDesign() {
    try (Stream<Path> folders = Files.list(Path.of("..", "roles"))) {
      return folders
          .filter(Files::isDirectory)
          .filter(folder ->
              Files.exists(folder.resolve("brief.md.dist"))
                  || Files.exists(folder.resolve("role.yml.dist")))
          .filter(folder -> !Files.exists(folder.resolve("design.md")))
          .map(folder -> "role folder without design.md: " + folder.getFileName())
          .toList()
          .stream();
    } catch (IOException unread) {
      throw new UncheckedIOException(unread);
    }
  }

  private static Stream<String> declaredModulesGone() {
    return Stream.of(
            RoleModules.ARBITER.stream(),
            RoleModules.CHECKERS.values().stream().flatMap(List::stream),
            RoleModules.STOP_KIND.stream(),
            RoleModules.CHECKER_KIND.stream())
        .flatMap(prefixes -> prefixes)
        .filter(prefix ->
            MainClasses.CODE.stream().noneMatch(type -> type.getName().startsWith(prefix)))
        .map(prefix -> "a declared module names nothing: " + prefix);
  }
}
