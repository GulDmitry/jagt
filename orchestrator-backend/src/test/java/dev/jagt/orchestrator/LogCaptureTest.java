package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;

class LogCaptureTest {

  private static final String LIST_APPENDER = "ch.qos.logback.core.read.ListAppender";

  private static final Set<String> SKIPPED =
      Set.of(
          "dev.jagt.orchestrator.adapter.ProcessRunnerTest",
          "dev.jagt.orchestrator.adapter.replay.ReplaySessionsTest",
          "dev.jagt.orchestrator.service.AgentSessionsTest",
          "dev.jagt.orchestrator.service.AgentStatusReportsTest",
          "dev.jagt.orchestrator.service.HumanWaitRecorderTest",
          "dev.jagt.orchestrator.service.master.ChargedReviewsTest",
          "dev.jagt.orchestrator.service.master.MasterPanelTest",
          "dev.jagt.orchestrator.service.RoleConfigOriginsTest",
          "dev.jagt.orchestrator.service.ScenarioWindowsTest",
          "dev.jagt.orchestrator.service.SessionTransitionLogTest",
          "dev.jagt.orchestrator.service.TaskTurnsTest",
          "dev.jagt.orchestrator.surface.board.AbortedConnectionFilterTest",
          "dev.jagt.orchestrator.surface.ui.BoardBannerTest",
          "dev.jagt.orchestrator.surface.ui.LogFileRollTest",
          "dev.jagt.orchestrator.eval.EvalQuorumTest");

  @Test
  void noTestHoldsLockOrRunsOnOneThread() {
    classes()
        .that(notSkipped())
        .should(holdNoLockAndNoSameThread())
        .allowEmptyShould(true)
        .check(SourceRoots.ALL);
  }

  @Test
  void logIsCapturedOnlyThroughTheFake() {
    classes()
        .that(notSkipped())
        .should(buildNoListAppender())
        .allowEmptyShould(true)
        .check(SourceRoots.ALL);
  }

  private static DescribedPredicate<JavaClass> notSkipped() {
    return DescribedPredicate.describe(
        "are not on the skipped list", type -> !SKIPPED.contains(topLevel(type)));
  }

  private static String topLevel(JavaClass type) {
    String name = type.getName();
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  private static ArchCondition<JavaClass> holdNoLockAndNoSameThread() {
    return new ArchCondition<>("hold no lock, no @Isolated and no same-thread execution") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        if (annotations(type).anyMatch(LogCaptureTest::isLockOrSameThread)) {
          events.add(SimpleConditionEvent.violated(type, type.getName()));
        }
      }
    };
  }

  private static Stream<JavaAnnotation<?>> annotations(JavaClass type) {
    Stream<JavaAnnotation<?>> own = type.getAnnotations().stream().map(a -> a);
    Stream<JavaAnnotation<?>> onMethods =
        type.getMethods().stream()
            .map(JavaMethod::getAnnotations)
            .flatMap(Set::stream)
            .map(a -> a);
    return Stream.concat(own, onMethods);
  }

  private static boolean isLockOrSameThread(JavaAnnotation<?> annotation) {
    String name = annotation.getRawType().getName();
    boolean lock =
        List.of(
                ResourceLock.class.getName(),
                ResourceLocks.class.getName(),
                Isolated.class.getName())
            .contains(name);
    boolean sameThread =
        name.equals("org.junit.jupiter.api.parallel.Execution")
            && annotation.get("value").filter(LogCaptureTest::isSameThread).isPresent();
    return lock || sameThread;
  }

  private static boolean isSameThread(Object mode) {
    return mode instanceof JavaEnumConstant constant && constant.name().equals("SAME_THREAD");
  }

  private static ArchCondition<JavaClass> buildNoListAppender() {
    return new ArchCondition<>("build no ListAppender") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        boolean builds =
            type.getConstructorCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getName().equals(LIST_APPENDER));
        if (builds) {
          events.add(SimpleConditionEvent.violated(type, type.getName()));
        }
      }
    };
  }
}
