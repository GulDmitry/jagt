package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class CodeCeilings {

  private static final String TASK_CAPABILITY =
      "dev.jagt.orchestrator.flow.capability.TaskCapability";
  private static final String STARTUP_CHECK = "dev.jagt.orchestrator.port.host.StartupCheck";
  private static final String PORT_PACKAGE = "dev.jagt.orchestrator.port";
  private static final Pattern BOOTS_CONTEXT =
      Pattern.compile(
          "(?m)^import org\\.springframework\\.boot\\."
              + "(test\\.context\\.SpringBootTest|builder\\.SpringApplicationBuilder);");

  private CodeCeilings() {}

  static List<String> capabilities() {
    return names(CeilingKind.concrete(TASK_CAPABILITY));
  }

  static List<String> startupChecks() {
    return names(CeilingKind.concrete(STARTUP_CHECK));
  }

  static List<String> ports() {
    return names(
        MainClasses.CODE.stream()
            .filter(JavaClass::isInterface)
            .filter(type -> !type.isNestedClass())
            .filter(type -> type.getPackageName().startsWith(PORT_PACKAGE)));
  }

  static List<String> contextBootingTests() {
    return SourceRoots.ROOTS.stream()
        .filter(SourceRoots::isTest)
        .flatMap(SourceRoots::javaFilesUnder)
        .filter(file -> BOOTS_CONTEXT.matcher(SourceRoots.read(file)).find())
        .map(Path::toString)
        .sorted()
        .toList();
  }

  private static List<String> names(Stream<JavaClass> types) {
    return types.map(JavaClass::getName).sorted().toList();
  }
}
