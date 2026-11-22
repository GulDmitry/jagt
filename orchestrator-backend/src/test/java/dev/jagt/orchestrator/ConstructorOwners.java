package dev.jagt.orchestrator;

import ch.qos.logback.core.spi.LifeCycle;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public final class ConstructorOwners {

  private ConstructorOwners() {}

  public static DescribedPredicate<JavaConstructor> notCalledFromMain() {
    return DescribedPredicate.describe(
        "is never called from main",
        constructor ->
            constructor.getCallsOfSelf().stream()
                .allMatch(call ->
                    call.getOrigin() instanceof JavaConstructor
                        && call.getOriginOwner().equals(constructor.getOwner())));
  }

  public static DescribedPredicate<JavaConstructor> springOrRecordOwns() {
    return DescribedPredicate.describe(
        "be a Spring bean's, Logback component's or spring.factories type's only constructor"
            + " or a record's canonical one",
        ConstructorOwners::owns);
  }

  private static boolean owns(JavaConstructor constructor) {
    JavaClass owner = constructor.getOwner();
    boolean canonical =
        owner.isRecord()
            && constructor.getRawParameterTypes().size()
                == owner.getFields().stream()
                    .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                    .count();
    boolean theBeansOnly =
        owner.getConstructors().size() == 1
            && (owner.isMetaAnnotatedWith(Component.class)
                || owner.isAnnotatedWith(ConfigurationProperties.class)
                || owner.isAnnotatedWith(Configuration.class)
                || owner.isAssignableTo(LifeCycle.class)
                || owner.isAssignableTo(FailureAnalyzer.class)
                || owner.isAssignableTo(EnvironmentPostProcessor.class));
    return canonical || theBeansOnly;
  }
}
