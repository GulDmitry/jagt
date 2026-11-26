package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

class InjectedListsTest {

  @Test
  void noBeanInjectsListsOfRecordsNoBeanProvides() {
    List<String> provided =
        MainClasses.CODE.stream()
            .flatMap(owner -> owner.getMethods().stream())
            .filter(method -> method.isAnnotatedWith(Bean.class))
            .map(JavaMethod::getRawReturnType)
            .map(JavaClass::getName)
            .toList();

    List<String> unprovided =
        MainClasses.CODE.stream()
            .filter(owner -> owner.isMetaAnnotatedWith(Component.class))
            .flatMap(owner -> owner.getConstructors().stream())
            .flatMap(constructor ->
                constructor.getParameterTypes().stream()
                    .filter(JavaParameterizedType.class::isInstance)
                    .map(JavaParameterizedType.class::cast)
                    .filter(list -> list.toErasure().isAssignableTo(List.class))
                    .flatMap(list -> list.getActualTypeArguments().stream())
                    .map(JavaType::toErasure)
                    .filter(JavaClass::isRecord)
                    .filter(element -> !element.isMetaAnnotatedWith(Component.class))
                    .filter(element -> !provided.contains(element.getName()))
                    .map(element ->
                        constructor.getOwner().getName()
                            + " injects List<"
                            + element.getName()
                            + ">"))
            .toList();

    assertThat(unprovided).isEmpty();
  }
}
