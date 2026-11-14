package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class MockedTypes {

  private static final Pattern A_TEST = Pattern.compile("@(Parameterized)?Test\\b");
  private static final Pattern A_MOCK =
      Pattern.compile(
          "mock\\(\\s*([A-Z]\\w*)\\.class|@Mock(?:itoBean)?\\b[^;]*?\\b([A-Z]\\w*)(?:<[^>]*>)?"
              + "\\s+\\w+\\s*[;=]");

  private MockedTypes() {}

  static Stream<String> overTheCeiling(Path test) {
    String[] parts = A_TEST.split(SourceRoots.read(test));
    Set<String> shared = mocked(parts[0]);
    return Arrays.stream(parts)
        .skip(1)
        .map(body ->
            Stream.concat(shared.stream(), mocked(body).stream())
                .collect(Collectors.toCollection(TreeSet::new)))
        .filter(mocks -> mocks.size() > CollaboratorCeilingTest.CEILING)
        .map(mocks -> test.getFileName() + " " + mocks)
        .distinct();
  }

  static Stream<Path> unitTests() {
    return SourceRoots.ROOTS.stream()
        .filter(SourceRoots::isTest)
        .flatMap(SourceRoots::javaFilesUnder)
        .filter(path -> path.getFileName().toString().endsWith("Test.java"));
  }

  private static Set<String> mocked(String source) {
    return A_MOCK
        .matcher(source)
        .results()
        .map(match -> match.group(1) != null ? match.group(1) : match.group(2))
        .collect(Collectors.toSet());
  }
}
