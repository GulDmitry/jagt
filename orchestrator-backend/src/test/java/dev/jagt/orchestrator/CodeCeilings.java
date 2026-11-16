package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.List;
import java.util.stream.Stream;

final class CodeCeilings {

  private static final String TASK_CAPABILITY =
      "dev.jagt.orchestrator.flow.capability.TaskCapability";
  private static final String STARTUP_CHECK = "dev.jagt.orchestrator.port.host.StartupCheck";
  private static final String PORT_PACKAGE = "dev.jagt.orchestrator.port";

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

  private static List<String> names(Stream<JavaClass> types) {
    return types.map(JavaClass::getName).sorted().toList();
  }
}
