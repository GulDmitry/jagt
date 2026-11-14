package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KindsTest {
  private static final Set<String> USE_CASES =
      Set.of("capability", "command", "job", "notify", "startup", "service");
  private static final Set<String> FACES = Set.of("Jobs", "Notifications", "PointWatch");

  @Test
  void serviceClassOneKindAloneReadsLivesInThatKind() {
    assertThat(
            ClassKinds.topLevel()
                .filter(type -> ClassKinds.kind(type).equals("service"))
                .filter(type -> {
                  Set<String> readers = ClassKinds.readers(type);
                  return readers.size() == 1
                      && !readers.contains("service")
                      && !readers.contains("surface");
                })
                .map(type -> type.getSimpleName() + " " + ClassKinds.readers(type)))
        .isEmpty();
  }

  @Test
  void anotherUseCaseReadsKindOnlyThroughItsRegistry() {
    assertThat(
            ClassKinds.topLevel()
                .filter(type ->
                    USE_CASES.contains(ClassKinds.kind(type))
                        && !ClassKinds.kind(type).equals("service"))
                .filter(type -> !FACES.contains(type.getSimpleName()))
                .filter(type ->
                    ClassKinds.readers(type).stream()
                        .map(reader -> reader.split("/", 0)[0])
                        .anyMatch(reader ->
                            USE_CASES.contains(reader) && !reader.equals(ClassKinds.kind(type))))
                .map(type -> type.getSimpleName() + " " + ClassKinds.readers(type)))
        .isEmpty();
  }

  @Test
  void readsEveryKindItJudges() {
    assertThat(ClassKinds.topLevel().map(ClassKinds::kind).collect(Collectors.toSet()))
        .containsAll(USE_CASES)
        .contains("service/master", "surface");
  }
}
