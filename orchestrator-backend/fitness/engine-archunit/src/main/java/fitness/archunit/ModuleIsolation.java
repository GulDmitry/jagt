package fitness.archunit;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.library.modules.ArchModule;
import com.tngtech.archunit.library.modules.ArchModules;
import fitness.core.FitnessConfigException;
import fitness.core.Measurement;
import fitness.core.Project;
import fitness.core.Reason;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** A class of one declared module may reference another declared module only through the packages it exposes. */
final class ModuleIsolation {

  private static final String EVERY_PACKAGE = "..";

  private ModuleIsolation() {}

  static Measurement measure(JavaClasses classes, Project project) {
    if (project.modules().isEmpty()) {
      throw new FitnessConfigException(Reason.MISSING_KEY, "project.modules");
    }
    Path root = project.root().toAbsolutePath().normalize();
    ArchModules<Exposing> modules =
        ArchModules.defineBy(
                (JavaClass type) -> identifier(type, root, project.modules().keySet()))
            .describeBy((identifier, members) -> exposing(identifier, project))
            .modularize(classes);
    return Measurement.of(
        modules.stream()
            .flatMap(module -> module.getModuleDependenciesFromSelf().stream())
            .flatMap(
                dependency ->
                    dependency.toClassDependencies().stream()
                        .filter(use -> !exposed(dependency.getTarget(), use))
                        .map(use -> item(dependency.getOrigin(), dependency.getTarget(), use)))
            .distinct()
            .toList());
  }

  private static Exposing exposing(ArchModule.Identifier identifier, Project project) {
    String name = identifier.iterator().next();
    return new Exposing(
        name,
        project.modules().get(name).stream()
            .map(ModuleIsolation::matcher)
            .toList());
  }

  private static ArchModule.Identifier identifier(JavaClass type, Path root, Set<String> names) {
    URI source = type.getSource().map(s -> s.getUri()).orElse(null);
    if (source == null || !"file".equals(source.getScheme())) {
      return ArchModule.Identifier.ignore();
    }
    Path inside = root.relativize(Path.of(source));
    if (inside.startsWith("..") || !names.contains(inside.getName(0).toString())) {
      return ArchModule.Identifier.ignore();
    }
    return ArchModule.Identifier.from(inside.getName(0).toString());
  }

  private static Predicate<String> matcher(String entry) {
    if (EVERY_PACKAGE.equals(entry)) {
      return name -> true;
    }
    PackageMatcher matcher = PackageMatcher.of(entry);
    return matcher::matches;
  }

  private static boolean exposed(ArchModule<Exposing> target, Dependency use) {
    String name = use.getTargetClass().getPackageName();
    return target.getDescriptor().exposes().stream().anyMatch(matcher -> matcher.test(name));
  }

  private static String item(ArchModule<Exposing> from, ArchModule<Exposing> to, Dependency use) {
    return from.getName()
        + " -> "
        + to.getName()
        + ": "
        + outermost(use.getOriginClass()).getName()
        + " -> "
        + outermost(use.getTargetClass()).getName();
  }

  private static JavaClass outermost(JavaClass type) {
    return type.getEnclosingClass().map(ModuleIsolation::outermost).orElse(type);
  }

  private record Exposing(String name, List<Predicate<String>> exposes)
      implements ArchModule.Descriptor {

    @Override
    public String getName() {
      return name;
    }
  }
}
