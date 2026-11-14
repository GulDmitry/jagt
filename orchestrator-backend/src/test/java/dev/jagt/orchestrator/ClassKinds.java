package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ClassKinds {

  private ClassKinds() {}

  static Stream<JavaClass> topLevel() {
    return MainClasses.CODE.stream().filter(JavaClass::isTopLevelClass);
  }

  static Set<String> readers(JavaClass type) {
    return MainClasses.CODE.stream()
        .filter(own -> isPartOf(own, type))
        .flatMap(own -> own.getDirectDependenciesToSelf().stream())
        .map(Dependency::getOriginClass)
        .filter(origin -> !isPartOf(origin, type))
        .map(ClassKinds::kind)
        .filter(kind -> !kind.equals("root"))
        .collect(Collectors.toSet());
  }

  static String kind(JavaClass type) {
    String[] packages =
        (type.getPackageName() + ".")
            .replaceFirst("^dev\\.jagt\\.orchestrator\\.?", "")
            .split("\\.", 0);
    if (packages[0].isEmpty()) {
      return "root";
    }
    return packages[0].equals("service") && packages.length > 1
        ? "service/" + packages[1]
        : packages[0];
  }

  private static boolean isPartOf(JavaClass candidate, JavaClass type) {
    return candidate.getName().equals(type.getName())
        || candidate.getName().startsWith(type.getName() + "$");
  }
}
