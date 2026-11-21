package fitness.core.run;

import fitness.core.Direction;
import fitness.core.FitnessFunction;
import fitness.core.Threshold;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Measures every function of a block outside a test run and prints each function's value against its threshold. */
public final class FitnessRun {

  private FitnessRun() {}

  /** Arguments: the fitness.yml, then optionally a block of the reserved tree. */
  public static void main(String[] args) {
    Block block = args.length > 1 ? Block.of(args[1]) : Block.ALL;
    List<String> failures = run(Path.of(args[0]), block, System.out);
    failures.forEach(System.err::println);
    if (!failures.isEmpty()) {
      System.exit(1);
    }
  }

  public static List<String> run(Path file, Block block, PrintStream out) {
    Evaluation evaluation = Evaluation.load(file);
    List<FitnessFunction> functions = evaluation.functions(block);
    List<String> failures = new ArrayList<>();
    evaluation
        .skipped()
        .forEach(
            missing ->
                out.printf("fitness: skipped %s: %s is not installed%n", missing.function().id(), missing.need()));
    for (FitnessFunction function : functions) {
      Report report = new Report(function, evaluation.measure(function));
      Threshold threshold = function.threshold();
      out.printf(
          "fitness: %s: value %d, %s %d%n",
          function.id(),
          report.measurement().value(),
          threshold.direction() == Direction.MAX ? "max" : "min",
          threshold.limit());
      report.failure().ifPresent(failures::add);
    }
    out.printf("fitness: %d functions, %d failed%n", functions.size(), failures.size());
    return failures;
  }
}
