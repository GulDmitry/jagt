package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import ch.qos.logback.core.read.ListAppender;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;

class LogCaptureTest {

  @Test
  void testCapturingLogHoldsTheGlobalLock() {
    methods()
        .that()
        .areAnnotatedWith(Test.class)
        .or()
        .areAnnotatedWith(ParameterizedTest.class)
        .should(holdTheGlobalLockWhenCapturingLog())
        .allowEmptyShould(true)
        .check(SourceRoots.ALL);
  }

  private static ArchCondition<JavaMethod> holdTheGlobalLockWhenCapturingLog() {
    return new ArchCondition<>("hold the global lock when capturing a log") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (capturesLog(method) && !holdsTheGlobalLock(method)) {
          events.add(SimpleConditionEvent.violated(method, method.getFullName()));
        }
      }
    };
  }

  private static boolean capturesLog(JavaMethod method) {
    return buildsOrReceivesAppender(method)
        || method.getMethodCallsFromSelf().stream()
            .filter(call -> call.getTargetOwner().equals(method.getOwner()))
            .flatMap(call -> call.getTarget().resolveMember().stream())
            .anyMatch(LogCaptureTest::buildsOrReceivesAppender);
  }

  private static boolean buildsOrReceivesAppender(JavaMethod method) {
    boolean builds =
        method.getConstructorCallsFromSelf().stream()
            .anyMatch(call -> call.getTargetOwner().isEquivalentTo(ListAppender.class));
    boolean receives =
        method.getMethodCallsFromSelf().stream()
            .anyMatch(
                call -> call.getTarget().getRawReturnType().isEquivalentTo(ListAppender.class));
    return builds || receives;
  }

  private static boolean holdsTheGlobalLock(JavaMethod method) {
    if (method.getOwner().isAnnotatedWith(Isolated.class)) {
      return true;
    }
    return method.tryGetAnnotationOfType(ResourceLock.class.getName()).stream()
        .anyMatch(lock -> lock.get("value").filter(Resources.GLOBAL::equals).isPresent());
  }
}
