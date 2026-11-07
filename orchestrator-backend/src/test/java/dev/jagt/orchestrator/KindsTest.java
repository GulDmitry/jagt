package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class KindsTest {
  private static final Set<String> USE_CASES =
      Set.of("capability", "command", "job", "notify", "startup", "service");
  private static final Set<String> FACES = Set.of("Jobs", "Notifications");

  @Test
  void aServiceClassOneKindAloneReadsLivesInThatKind() {
    assertThat(
            topLevel()
                .filter(type -> kind(type).equals("service"))
                .filter(
                    type -> {
                      Set<String> readers = readers(type);
                      return readers.size() == 1
                          && !readers.contains("service")
                          && !readers.contains("surface");
                    })
                .map(type -> type.getSimpleName() + " " + readers(type)))
        .isEmpty();
  }

  @Test
  void anotherUseCaseReadsAKindOnlyThroughItsRegistry() {
    assertThat(
            topLevel()
                .filter(type -> USE_CASES.contains(kind(type)) && !kind(type).equals("service"))
                .filter(type -> !FACES.contains(type.getSimpleName()))
                .filter(
                    type ->
                        readers(type).stream()
                            .map(reader -> reader.split("/")[0])
                            .anyMatch(
                                reader -> USE_CASES.contains(reader) && !reader.equals(kind(type))))
                .map(type -> type.getSimpleName() + " " + readers(type)))
        .isEmpty();
  }

  @Test
  void readsEveryKindItJudges() {
    assertThat(topLevel().map(KindsTest::kind).collect(Collectors.toSet()))
        .containsAll(USE_CASES)
        .contains("service/master", "surface");
  }

  private static Stream<JavaClass> topLevel() {
    return MainClasses.CODE.stream().filter(JavaClass::isTopLevelClass);
  }

  private static Set<String> readers(JavaClass type) {
    return MainClasses.CODE.stream()
        .filter(own -> isPartOf(own, type))
        .flatMap(own -> own.getDirectDependenciesToSelf().stream())
        .map(Dependency::getOriginClass)
        .filter(origin -> !isPartOf(origin, type))
        .map(KindsTest::kind)
        .filter(kind -> !kind.equals("root"))
        .collect(Collectors.toSet());
  }

  private static boolean isPartOf(JavaClass candidate, JavaClass type) {
    return candidate.getName().equals(type.getName())
        || candidate.getName().startsWith(type.getName() + "$");
  }

  private static String kind(JavaClass type) {
    String[] packages =
        (type.getPackageName() + ".")
            .replaceFirst("^dev\\.jagt\\.orchestrator\\.?", "")
            .split("\\.");
    if (packages[0].isEmpty()) {
      return "root";
    }
    return packages[0].equals("service") && packages.length > 1
        ? "service/" + packages[1]
        : packages[0];
  }
}
