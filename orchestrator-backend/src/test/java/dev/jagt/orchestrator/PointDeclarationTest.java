package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.role.Point;
import dev.jagt.orchestrator.flow.role.Points;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointDeclarationTest {

  @Test
  void pointIsDeclaredOnlyInFlowPointFiles() {
    List<Path> declaring =
        SourceRoots.mainJavaFiles()
            .filter(path -> !path.getFileName().toString().startsWith("Point"))
            .filter(path -> SourceRoots.read(path).contains("new Point("))
            .toList();

    assertThat(declaring).isEmpty();
  }

  @Test
  void everyBuiltInPointHasOneFixtureRowAndEveryRowOnePoint() throws IOException {
    try (InputStream rows = getClass().getResourceAsStream("/points.txt")) {
      List<String> fixture =
          List.of(new String(rows.readAllBytes(), StandardCharsets.UTF_8).split("\n"));

      assertThat(Points.builtIn()).extracting(Point::id).containsExactlyElementsOf(fixture);
    }
  }

  @Test
  void onlyHousekeepingInstallUsageAreRaisedWithoutStoredFact() {
    assertThat(Points.builtIn())
        .filteredOn(Point::raised)
        .extracting(Point::id)
        .containsExactly("housekeeping", "install", "usage");
  }
}
