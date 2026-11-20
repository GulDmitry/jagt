package fitness.junit;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import fitness.core.Block;
import fitness.core.Evaluation;
import fitness.core.Report;
import fitness.core.ReservedTree;
import fitness.core.FitnessFunction;
import fitness.core.Section;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicNode;

public final class FitnessTests {

  private static final String ONLY = "fitness.only";

  private final Evaluation evaluation;
  private final String only;

  private FitnessTests(Evaluation evaluation, String only) {
    this.evaluation = evaluation;
    this.only = only;
  }

  public static Stream<DynamicNode> from(Path file) {
    Evaluation evaluation = Evaluation.load(file, Set.of("junit"));
    String only = System.getProperty(ONLY);
    if (only != null
        && evaluation.config().functions().stream().noneMatch(function -> function.id().equals(only))) {
      throw new IllegalArgumentException("-P" + ONLY + " names no function: " + only);
    }
    return new FitnessTests(evaluation, only).dimensions();
  }

  private Stream<DynamicNode> dimensions() {
    return ReservedTree.sections().stream()
        .map(Section::dimension)
        .distinct()
        .map(dimension -> dynamicContainer(dimension, characteristics(dimension)));
  }

  private Stream<DynamicNode> characteristics(String dimension) {
    return ReservedTree.sections().stream()
        .filter(section -> section.dimension().equals(dimension))
        .map(Section::characteristic)
        .distinct()
        .map(
            characteristic ->
                dynamicContainer(
                    characteristic, subcharacteristics(dimension, characteristic).toList()));
  }

  private Stream<DynamicNode> subcharacteristics(String dimension, String characteristic) {
    return ReservedTree.sections().stream()
        .filter(section -> section.dimension().equals(dimension))
        .filter(section -> section.characteristic().equals(characteristic))
        .map(
            section ->
                dynamicContainer(section.subcharacteristic(), tests(section).toList()));
  }

  private Stream<DynamicNode> tests(Section section) {
    Predicate<FitnessFunction> selected = function -> only == null || function.id().equals(only);
    Stream<DynamicNode> measured =
        evaluation.functions(new Block(section.path())).stream()
            .filter(function -> function.section().equals(section.path()))
            .filter(selected)
            .map(function -> dynamicTest(function.id(), () -> check(function)));
    Stream<DynamicNode> skipped =
        evaluation.skipped().stream()
            .filter(missing -> missing.function().section().equals(section.path()))
            .filter(missing -> selected.test(missing.function()))
            .map(
                missing ->
                    dynamicTest(
                        missing.function().id(),
                        () -> Assumptions.abort(missing.need() + " is not installed")));
    return Stream.concat(measured, skipped);
  }

  private void check(FitnessFunction function) {
    new Report(function, evaluation.measure(function)).failure().ifPresent(Assertions::fail);
  }
}
