package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;

enum CeilingKind {
  CI_JOBS("ciJobs"),
  CI_TRIGGERS("ciTriggers"),
  SOURCE_SETS("sourceSets"),
  START_TRIGGERS("startTriggers"),
  TASK_STATUSES("taskStatuses"),
  PUBLIC_SURFACE("publicSurface"),
  GRADLE_MODULES("gradleModules"),
  BUILD_LOGIC("buildLogic"),
  DEPENDENCY_LINES("dependencyLinesInBuildGradle"),
  CAPABILITIES("capabilities"),
  PORTS("ports"),
  STARTUP_CHECKS("startupChecks"),
  ROLES("roles"),
  SPEC_CAPABILITIES("specCapabilities"),
  RULE_DOCS("ruleDocs"),
  CONTEXT_BOOTING_TESTS("contextBootingTests");

  private static final Path BACKEND = Path.of(".");
  private static final String JOB = "dev.jagt.orchestrator.job.Job";
  private static final String GLOBAL_COMMAND = "dev.jagt.orchestrator.command.GlobalCommand";
  private static final String TASK_STATUS = "dev.jagt.orchestrator.task.TaskStatus";
  private static final String TOOL_REGISTRY = "dev.jagt.orchestrator.surface.mcp.McpToolRegistry";

  private final String key;

  CeilingKind(String key) {
    this.key = key;
  }

  String key() {
    return key;
  }

  List<String> items() {
    return switch (this) {
      case CI_JOBS -> ciJobs();
      case CI_TRIGGERS -> CiWorkflow.githubTriggers().stream().sorted().toList();
      case SOURCE_SETS -> sourceSets();
      case START_TRIGGERS -> startTriggers();
      case TASK_STATUSES -> taskStatuses();
      case PUBLIC_SURFACE -> publicSurface();
      case GRADLE_MODULES -> BuildCeilings.gradleModules();
      case BUILD_LOGIC -> BuildCeilings.buildLogic();
      case DEPENDENCY_LINES -> BuildCeilings.dependencyLinesInBuildGradle();
      case CAPABILITIES -> CodeCeilings.capabilities();
      case PORTS -> CodeCeilings.ports();
      case STARTUP_CHECKS -> CodeCeilings.startupChecks();
      case ROLES -> BuildCeilings.roles();
      case SPEC_CAPABILITIES -> BuildCeilings.specCapabilities();
      case RULE_DOCS -> BuildCeilings.ruleDocs();
      case CONTEXT_BOOTING_TESTS -> CodeCeilings.contextBootingTests();
    };
  }

  private static List<String> ciJobs() {
    return Stream.concat(
            CiWorkflow.jobs().keySet().stream().map(job -> "github " + job),
            CiWorkflow.gitlabJobs().stream().map(job -> "gitlab " + job))
        .sorted()
        .toList();
  }

  private static List<String> sourceSets() {
    try (Stream<Path> modules = Files.list(BACKEND)) {
      return Stream.concat(Stream.of(BACKEND), modules)
          .filter(module -> !module.getFileName().toString().equals("build-logic"))
          .map(module -> module.resolve("src"))
          .filter(Files::isDirectory)
          .flatMap(CeilingKind::setsUnder)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Stream<String> setsUnder(Path src) {
    try (Stream<Path> sets = Files.list(src)) {
      return sets
          .filter(Files::isDirectory)
          .filter(set -> !set.getFileName().toString().startsWith("."))
          .map(set -> src.getParent() + "/" + set.getFileName())
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> startTriggers() {
    return Stream.concat(
            concrete(JOB).map(type -> "job " + type.getName()),
            methods()
                .filter(method ->
                    method.isAnnotatedWith(EventListener.class)
                        || method.isAnnotatedWith(Scheduled.class))
                .map(method -> "listener " + method.getFullName()))
        .sorted()
        .toList();
  }

  private static List<String> taskStatuses() {
    return MainClasses.CODE.get(TASK_STATUS).getEnumConstants().stream()
        .map(JavaEnumConstant::name)
        .sorted()
        .toList();
  }

  private static List<String> publicSurface() {
    Stream<String> http =
        methods().filter(CeilingKind::mapsHttp).map(method -> "http " + method.getFullName());
    Stream<String> commands = concrete(GLOBAL_COMMAND).map(type -> "command " + type.getName());
    Stream<String> tools =
        MainClasses.CODE.stream()
            .flatMap(type -> type.getMethodCallsFromSelf().stream())
            .filter(call -> call.getTargetOwner().getName().equals(TOOL_REGISTRY))
            .filter(call -> call.getName().equals("tool"))
            .map(call -> "tool " + call.getOrigin().getFullName() + ":" + call.getLineNumber());
    return Stream.of(http, commands, tools).flatMap(items -> items).sorted().toList();
  }

  static Stream<JavaClass> concrete(String supertype) {
    return MainClasses.CODE.stream()
        .filter(type -> type.isAssignableTo(supertype) && !type.getName().equals(supertype))
        .filter(
            type -> !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT));
  }

  private static Stream<JavaMethod> methods() {
    return MainClasses.CODE.stream().flatMap(type -> type.getMethods().stream());
  }

  private static boolean mapsHttp(JavaMethod method) {
    return method.getAnnotations().stream()
        .map(JavaAnnotation::getRawType)
        .anyMatch(type ->
            type.isEquivalentTo(RequestMapping.class)
                || type.isMetaAnnotatedWith(RequestMapping.class));
  }
}
