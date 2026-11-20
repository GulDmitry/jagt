package fitness.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import technical.maintainability.analysability.FakeFitness;

class FitnessLoaderAcceptsTest {

  private final FitnessLoader loader =
      new FitnessLoader(List.of(new FakeEngine()), List.of(new FakeFitness()));

  @Test
  void loadsAnEmptyReservedSection() {
    FitnessConfig config =
        loader.parse("schema: 1\ntechnical: { maintainability: { reusability: {} } }", Path.of("."));

    assertThat(config.functions()).isEmpty();
  }

  @Test
  void readsARuleWithItsThresholdSelectorsAndSection() {
    FitnessConfig config =
        loader.parse(
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, min: 2, per: line, decision: '0176', axes: { scope: holistic } } } } }",
            Path.of("."));

    FitnessFunction function = config.functions().getFirst();
    assertThat(function.threshold()).isEqualTo(new Threshold(Direction.MIN, 2));
    assertThat(function.section()).isEqualTo("technical.maintainability.modularity");
    assertThat(function.selectors()).containsEntry("per", "line");
    assertThat(function.decision()).isEqualTo("0176");
    assertThat(function.axes()).containsEntry("scope", "holistic");
  }

  @Test
  void keepsTheHintOfARule() {
    FitnessConfig config =
        loader.parse(
            "schema: 1\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 2, hint: 'compress it' } } } }",
            Path.of("."));

    assertThat(config.functions().getFirst().selectors()).containsEntry("hint", "compress it");
  }

  @Test
  void aFitnessClassInItsSectionsPackageIsAccepted() {
    FitnessConfig config =
        loader.parse(
            "schema: 1\ntechnical: { maintainability: { analysability: { own: { fitness: FakeFitness, governs: count, max: 0 } } } }",
            Path.of("."));

    assertThat(config.functions().getFirst().selectors()).containsEntry("fitness", "FakeFitness");
  }

  @Test
  void aFitnessFunctionOfTheProjectReplacesTheInheritedFitnessFunctionOfTheSameId() {
    FitnessConfig config =
        loader.parse(
            "schema: 1\nextends: fixture-base\ntechnical: { maintainability: { modularity: { ports: { engine: fake, governs: count, max: 3 } } } }",
            Path.of("."));

    assertThat(config.functions()).hasSize(1);
    assertThat(config.functions().getFirst().threshold().limit()).isEqualTo(3);
  }

  @Test
  void inheritedRulesStayWhenTheProjectAddsAnother() {
    FitnessConfig config =
        loader.parse(
            "schema: 1\nextends: fixture-base\ntechnical: { maintainability: { reusability: { other: { engine: fake, governs: count, max: 1 } } } }",
            Path.of("."));

    assertThat(config.functions()).extracting(FitnessFunction::id).containsExactlyInAnyOrder("ports", "other");
  }

  @Test
  void resolvesTheProjectRootAgainstTheFilesDirectory() {
    FitnessConfig config =
        loader.parse("schema: 1\nproject: { root: .., basePackage: a.b }", Path.of("/x/y"));

    assertThat(config.project())
        .isEqualTo(new Project(Path.of("/x"), "a.b", List.of("all"), Map.of()));
  }
}
