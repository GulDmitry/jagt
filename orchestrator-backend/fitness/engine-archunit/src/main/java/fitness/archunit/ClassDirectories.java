package fitness.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import fitness.core.Project;
import fitness.core.SourceRoots;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Imports the compiled classes of a project's source sets once. */
final class ClassDirectories {

  private final Map<String, JavaClasses> imported = new ConcurrentHashMap<>();

  JavaClasses of(Project project, String set) {
    return imported.computeIfAbsent(
        project.root() + ":" + set,
        key -> new ClassFileImporter().importPaths(directories(project, set)));
  }

  private static List<Path> directories(Project project, String set) {
    return SourceRoots.of(project, set).stream()
        .map(source -> SourceRoots.classes(project.root(), source))
        .distinct()
        .filter(Files::isDirectory)
        .toList();
  }
}
