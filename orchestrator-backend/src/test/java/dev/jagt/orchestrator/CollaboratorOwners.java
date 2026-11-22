package dev.jagt.orchestrator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public final class CollaboratorOwners {

  private static final String MAIN = "/build/classes/java/main/";

  private CollaboratorOwners() {}

  public static DescribedPredicate<JavaClass> areMainClasses() {
    return DescribedPredicate.describe(
        "main classes", type -> TestKinds.location(type).contains(MAIN));
  }

  public static ArchCondition<JavaClass> buildNoCollaboratorOfTheirOwn() {
    return new ArchCondition<>("build no collaborator of their own") {
      @Override
      public void check(JavaClass owner, ConditionEvents events) {
        if (owner.isMetaAnnotatedWith(Configuration.class)
            || owner.isRecord()
            || owner.isAssignableTo(EnvironmentPostProcessor.class)) {
          return;
        }
        owner.getConstructorCallsFromSelf().stream()
            .filter(call -> buildsFromInstanceCode(call))
            .filter(call -> !owner.isAssignableTo(call.getTargetOwner().getName()))
            .filter(call -> !sameOutermost(owner, call.getTargetOwner()))
            .filter(call -> isCollaborator(call.getTargetOwner()))
            .filter(call -> !wrapsWhatOwnerHolds(owner, call))
            .filter(call ->
                call.getOrigin() instanceof JavaConstructor
                    || call.getTargetOwner().isMetaAnnotatedWith(Component.class))
            .forEach(
                call -> events.add(SimpleConditionEvent.violated(owner, call.getDescription())));
      }
    };
  }

  private static boolean wrapsWhatOwnerHolds(JavaClass owner, JavaConstructorCall call) {
    List<JavaClass> arguments = call.getTarget().getRawParameterTypes();
    Set<JavaClass> held = new HashSet<>();
    owner.getConstructors().forEach(constructor -> held.addAll(constructor.getRawParameterTypes()));
    owner.getFields().forEach(field -> held.add(field.getRawType()));
    return arguments.stream().anyMatch(held::contains)
        && arguments.stream().allMatch(type -> held.contains(type) || !isProjectObject(type));
  }

  private static boolean isProjectObject(JavaClass type) {
    return type.getPackageName().startsWith(SourceRoots.BASE_PACKAGE)
        && !type.isRecord()
        && !type.isEnum();
  }

  private static boolean sameOutermost(JavaClass owner, JavaClass target) {
    return outermost(owner).equals(outermost(target));
  }

  private static JavaClass outermost(JavaClass type) {
    return type.getEnclosingClass().map(CollaboratorOwners::outermost).orElse(type);
  }

  private static boolean buildsFromInstanceCode(JavaConstructorCall call) {
    if (call.getOrigin() instanceof JavaMethod method) {
      return !method.getModifiers().contains(JavaModifier.STATIC)
          && !method.isAnnotatedWith(Bean.class);
    }
    return true;
  }

  private static boolean isCollaborator(JavaClass target) {
    boolean ours = target.getPackageName().startsWith(SourceRoots.BASE_PACKAGE);
    boolean value =
        target.isRecord()
            || target.isEnum()
            || target.isInterface()
            || target.isAssignableTo(Throwable.class)
            || target.getSimpleName().endsWith("Builder");
    return ours
        && !value
        && (target.isMetaAnnotatedWith(Component.class) || holdsCollaborator(target));
  }

  private static boolean holdsCollaborator(JavaClass target) {
    return target.getFields().stream()
        .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
        .map(field -> field.getRawType())
        .anyMatch(CollaboratorOwners::isProjectObject);
  }
}
