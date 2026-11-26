package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

class BeanParametersTest {

  private static final List<Class<?>> WRAPPERS =
      List.of(Collection.class, Map.class, Optional.class, ObjectProvider.class);

  private static final List<String> FRAMEWORK_PACKAGES =
      List.of("org.springframework.", "tools.jackson.");

  @Test
  void failsBuildNamingBeanParameterWhenNoBeanProvidesItsType() {
    List<JavaClass> providers =
        Stream.concat(
                MainClasses.CODE.stream()
                    .filter(owner ->
                        owner.isMetaAnnotatedWith(Component.class)
                            || owner.isAnnotatedWith(ConfigurationProperties.class)),
                MainClasses.CODE.stream()
                    .flatMap(owner -> owner.getMethods().stream())
                    .filter(method -> method.isAnnotatedWith(Bean.class))
                    .map(JavaMethod::getRawReturnType))
            .toList();

    List<String> unprovided =
        Stream.concat(
                MainClasses.CODE.stream()
                    .filter(owner -> owner.isMetaAnnotatedWith(Component.class))
                    .flatMap(owner -> owner.getConstructors().stream())
                    .map(JavaCodeUnit.class::cast),
                MainClasses.CODE.stream()
                    .flatMap(owner -> owner.getMethods().stream())
                    .filter(method -> method.isAnnotatedWith(Bean.class)))
            .flatMap(unit ->
                unit.getParameters().stream()
                    .filter(parameter -> !parameter.isAnnotatedWith(Value.class))
                    .map(parameter ->
                        parameter.getType() instanceof JavaParameterizedType generic
                                && WRAPPERS.stream()
                                    .anyMatch(
                                        wrapper -> generic.toErasure().isAssignableTo(wrapper))
                            ? generic.getActualTypeArguments().getLast().toErasure()
                            : parameter.getType().toErasure())
                    .map(type -> type.isArray() ? type.getBaseComponentType() : type)
                    .filter(
                        type -> FRAMEWORK_PACKAGES.stream().noneMatch(type.getName()::startsWith))
                    .filter(type ->
                        providers.stream()
                            .noneMatch(provider -> provider.isAssignableTo(type.getName())))
                    .map(type ->
                        unit.getOwner().getName()
                            + " takes "
                            + type.getName()
                            + " no bean provides"))
            .toList();

    assertThat(unprovided).isEmpty();
  }
}
