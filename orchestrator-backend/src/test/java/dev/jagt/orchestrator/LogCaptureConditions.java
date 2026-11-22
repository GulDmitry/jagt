package dev.jagt.orchestrator;

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
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;

public final class LogCaptureConditions {

  private static final String LIST_APPENDER = "ch.qos.logback.core.read.ListAppender";

  private LogCaptureConditions() {}

  public static ArchCondition<JavaClass> holdNoLockAndNoSameThread() {
    return new ArchCondition<>("hold no lock, no @Isolated and no same-thread execution") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        if (annotations(type).anyMatch(LogCaptureConditions::isLockOrSameThread)) {
          events.add(SimpleConditionEvent.violated(type, type.getName()));
        }
      }
    };
  }

  public static ArchCondition<JavaClass> buildNoListAppender() {
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

  private static Stream<JavaAnnotation<?>> annotations(JavaClass type) {
    Stream<JavaAnnotation<?>> own = type.getAnnotations().stream().map(a -> a);
    Stream<JavaAnnotation<?>> onMethods =
        type.getMethods().stream().map(JavaMethod::getAnnotations).flatMap(Set::stream).map(a -> a);
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
            && annotation.get("value").filter(LogCaptureConditions::isSameThread).isPresent();
    return lock || sameThread;
  }

  private static boolean isSameThread(Object mode) {
    return mode instanceof JavaEnumConstant constant && constant.name().equals("SAME_THREAD");
  }
}
