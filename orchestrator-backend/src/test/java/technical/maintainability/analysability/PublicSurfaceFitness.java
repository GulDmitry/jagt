package technical.maintainability.analysability;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import dev.jagt.orchestrator.MainClasses;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.RequestMapping;

public final class PublicSurfaceFitness implements Fitness {

  private static final String GLOBAL_COMMAND = "dev.jagt.orchestrator.command.GlobalCommand";
  private static final String TOOL_REGISTRY = "dev.jagt.orchestrator.surface.mcp.McpToolRegistry";

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Stream<String> http =
        MainTypes.methods()
            .filter(PublicSurfaceFitness::mapsHttp)
            .map(method -> "http " + method.getFullName());
    Stream<String> commands =
        MainTypes.concrete(GLOBAL_COMMAND).map(type -> "command " + type.getName());
    Stream<String> tools =
        MainClasses.CODE.stream()
            .flatMap(type -> type.getMethodCallsFromSelf().stream())
            .filter(call -> call.getTargetOwner().getName().equals(TOOL_REGISTRY))
            .filter(call -> call.getName().equals("tool"))
            .map(call -> "tool " + call.getOrigin().getFullName() + ":" + call.getLineNumber());
    return Measurement.of(Stream.of(http, commands, tools).flatMap(items -> items).toList());
  }

  private static boolean mapsHttp(JavaMethod method) {
    return method.getAnnotations().stream()
        .map(JavaAnnotation::getRawType)
        .anyMatch(type ->
            type.isEquivalentTo(RequestMapping.class)
                || type.isMetaAnnotatedWith(RequestMapping.class));
  }
}
