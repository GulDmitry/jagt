package technical.maintainability.modularity;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import dev.jagt.orchestrator.MainClasses;
import dev.jagt.orchestrator.flow.Generation;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class FactGenerationsFitness implements Fitness {

  private static final String FLOW = "dev.jagt.orchestrator.flow";
  private static final String MOVE = FLOW + ".TaskMove$";

  private static final Set<String> FACT_EFFECTS =
      Set.of(
          "ReadsChecks",
          "FailsToReadChecks",
          "ReadsApproval",
          "SeesHead",
          "Signs",
          "MergesRequest",
          "MergesRequestByHost");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<JavaClass> moves = factMoves().distinct().toList();
    List<String> items =
        moves.isEmpty()
            ? List.of("no edge writes a read fact: " + FACT_EFFECTS)
            : moves.stream()
                .filter(move -> !carriesGeneration(move))
                .map(move -> "fact move without a generation: " + move.getSimpleName())
                .toList();
    return Measurement.of(items);
  }

  private static Stream<JavaClass> factMoves() {
    return MainClasses.CODE.stream()
        .filter(owner -> owner.getPackageName().equals(FLOW))
        .flatMap(owner -> owner.getMethods().stream())
        .filter(FactGenerationsFitness::writesReadFact)
        .flatMap(method -> method.getMethodCallsFromSelf().stream())
        .map(call -> call.getTargetOwner())
        .filter(owner -> owner.getName().startsWith(MOVE) && owner.isRecord());
  }

  private static boolean writesReadFact(JavaMethod method) {
    return method.getConstructorCallsFromSelf().stream()
        .map(call -> call.getTargetOwner().getName())
        .anyMatch(name ->
            FACT_EFFECTS.stream().anyMatch(effect -> name.equals(FLOW + ".TaskEffect$" + effect)));
  }

  private static boolean carriesGeneration(JavaClass move) {
    return Arrays.stream(move.reflect().getRecordComponents())
        .anyMatch(component -> component.getType().equals(Generation.class));
  }
}
