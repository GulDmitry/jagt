package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Fitness;
import fitness.core.ReservedTree;
import fitness.core.Section;
import fitness.junit.FitnessTests;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class FitnessTest {

  @TestFactory
  Stream<DynamicNode> everyRuleOfFitnessYmlHoldsItsNumber() {
    List<DynamicNode> dimensions = FitnessTests.from(Path.of("../fitness.yml")).toList();

    assertThat(dimensions).isNotEmpty();

    return dimensions.stream();
  }

  @Test
  void everyFitnessClassSitsInThePackageNamedBySomeSectionOfTheReservedTree() {
    List<String> sections = ReservedTree.sections().stream().map(Section::packageName).toList();

    assertThat(
            ServiceLoader.load(Fitness.class).stream().map(found -> found.type().getPackageName()))
        .isNotEmpty()
        .allSatisfy(name -> assertThat(sections).describedAs(name).contains(name));
  }
}
