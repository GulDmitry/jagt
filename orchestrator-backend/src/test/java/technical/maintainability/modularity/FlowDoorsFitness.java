package technical.maintainability.modularity;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.lang.ArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.MainClasses;
import dev.jagt.orchestrator.NamedClasses;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.task.turn.Turn;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class FlowDoorsFitness implements Fitness {

  private static final String REDUCER = "dev.jagt.orchestrator.flow.TaskReducer";
  private static final String STATE = "dev.jagt.orchestrator.task.TaskState";

  private static final List<List<String>> REDUCER_ONLY =
      List.of(
          List.of("withStatus"),
          List.of("withStop", "lifted"),
          List.of("withPlanGate"),
          List.of("withGitTrust"),
          List.of("withTold"),
          List.of("withRepoRequest", "withRepo", "withoutRepo"),
          List.of(
              "withChecksRead", "withChecksUnread", "withChecksReset", "withApproved", "withHead"),
          List.of("withInfraRetries"),
          List.of("withReport"),
          List.of("withTurn"),
          List.of("withDeployment"),
          List.of("withSession"));

  private static final List<String> SESSION_SIGN =
      List.of(
          "withHalt",
          "withQuietSince",
          "withMovedAt",
          "withWorkStartedAt",
          "withReportedAt",
          "withStampedAsk");

  private static final Path SOURCE =
      Path.of("src/test/java/technical/maintainability/modularity/FlowDoorsFitness.java");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.of(
                REDUCER_ONLY.stream().flatMap(names -> items(reducerOnly(names))),
                items(reviewRequest()),
                items(turnOpenedAndClosed()),
                items(roleAndMaster()),
                items(sessionSign()),
                items(noTaskJobWatchNamesThePause()),
                items(statusRestored()),
                namedClassesGone())
            .flatMap(items -> items)
            .toList());
  }

  private static Stream<String> items(ArchRule rule) {
    return ArchItems.of(rule, MainClasses.CODE).items().stream();
  }

  private static DescribedPredicate<JavaCall<?>> calling(Set<String> names) {
    return DescribedPredicate.describe(
        "a call to one of " + names, (JavaCall<?> call) -> names.contains(call.getName()));
  }

  private static ArchRule reducerOnly(List<String> names) {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName(REDUCER)
        .and()
        .doNotHaveFullyQualifiedName(STATE)
        .should()
        .callMethodWhere(calling(Set.copyOf(names)));
  }

  private static ArchRule reviewRequest() {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName(REDUCER)
        .and()
        .doNotHaveFullyQualifiedName(STATE)
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState$TaskStateBuilder")
        .should()
        .callMethodWhere(
            calling(
                Set.of(
                    "withMrUrl",
                    "withMrUrls",
                    "withReviewRound",
                    "withMerged",
                    "withMergedByHost")));
  }

  private static ArchRule turnOpenedAndClosed() {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName(REDUCER)
        .and()
        .doNotHaveFullyQualifiedName(STATE)
        .should()
        .callMethodWhere(target(name("withTurn")))
        .orShould()
        .callMethod(Turn.class, "relayed", String.class, String.class, String.class, long.class)
        .orShould()
        .callMethod(Turn.class, "closed", long.class);
  }

  private static ArchRule roleAndMaster() {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName(REDUCER)
        .and()
        .doNotHaveFullyQualifiedName(STATE)
        .should()
        .callMethod(
            dev.jagt.orchestrator.task.TaskState.class,
            "withRole",
            String.class,
            dev.jagt.orchestrator.task.turn.RoleRecord.class)
        .orShould()
        .callMethod(
            dev.jagt.orchestrator.task.TaskState.class,
            "withMaster",
            dev.jagt.orchestrator.task.master.Master.class);
  }

  private static ArchRule sessionSign() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("dev.jagt.orchestrator.flow..")
        .and()
        .doNotHaveFullyQualifiedName(STATE)
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.session.Session")
        .should()
        .callMethodWhere(calling(Set.copyOf(SESSION_SIGN)));
  }

  private static ArchRule noTaskJobWatchNamesThePause() {
    return noClasses()
        .that()
        .implement(TaskJob.class)
        .should()
        .accessField(StopKind.class, "PAUSE")
        .orShould()
        .callMethod(StopKind.class, "values");
  }

  private static ArchRule statusRestored() {
    return noClasses()
        .that()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.FlowReports")
        .and()
        .doNotHaveFullyQualifiedName(STATE)
        .should()
        .callMethodWhere(target(name("restoredFrom")));
  }

  private static Stream<String> namedClassesGone() {
    return NamedClasses.in(SOURCE).stream()
        .filter(name -> !MainClasses.CODE.contain(name))
        .map(name -> "the door names a class that is gone: " + name);
  }
}
