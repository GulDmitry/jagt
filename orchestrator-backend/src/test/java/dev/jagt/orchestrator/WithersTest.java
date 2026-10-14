package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WithersTest {

    private static final List<Class<?>> RECORDS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator")
            .stream().filter(JavaClass::isRecord).<Class<?>>map(JavaClass::reflect).toList();

    @Test
    void aWitherChangesItsOwnComponentAndNoOther() {
        assertThat(withers().map(WithersTest::defect).filter(defect -> !defect.isEmpty())).isEmpty();
    }

    @Test
    void readsEveryWitherItClaimsToCheck() {
        assertThat(withers().count()).isGreaterThan(80);
    }

    private record Wither(Class<?> type, RecordComponent component, Method method) {
    }

    private static Stream<Wither> withers() {
        return RECORDS.stream().flatMap(type -> Arrays.stream(type.getRecordComponents())
                .flatMap(component -> witherOf(type, component).stream()
                        .map(method -> new Wither(type, component, method))));
    }

    private static Optional<Method> witherOf(Class<?> type, RecordComponent component) {
        String name = "with" + Character.toUpperCase(component.getName().charAt(0)) + component.getName().substring(1);
        try {
            Method method = type.getMethod(name, component.getType());
            return method.getReturnType() == type ? Optional.of(method) : Optional.empty();
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static String defect(Wither wither) {
        String at = wither.type().getName() + "." + wither.method().getName();
        try {
            Object before = build(wither.type(), 0);
            Object changed = sample(wither.component().getGenericType(), 1);
            Object after = wither.method().invoke(before, changed);
            return Arrays.stream(wither.type().getRecordComponents())
                    .filter(other -> !equal(value(other, after),
                            other.getName().equals(wither.component().getName()) ? changed : value(other, before)))
                    .map(other -> at + " left " + other.getName() + " wrong")
                    .findFirst().orElse("");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return at + " could not be exercised: " + e;
        }
    }

    private static boolean equal(Object actual, Object expected) {
        return actual == null ? expected == null : actual.equals(expected);
    }

    private static Object value(RecordComponent component, Object of) {
        try {
            return component.getAccessor().invoke(of);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object build(Class<?> type, int variant) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        Constructor<?> canonical = type.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
        canonical.setAccessible(true);
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = sample(components[i].getGenericType(), variant);
        }
        return canonical.newInstance(values);
    }

    private static Object sample(Type type, int variant) throws ReflectiveOperationException {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] arguments = parameterized.getActualTypeArguments();
            if (raw == List.class) {
                return List.of(sample(arguments[0], variant));
            }
            if (raw == Set.class) {
                return Set.of(sample(arguments[0], variant));
            }
            if (raw == Map.class) {
                return Map.of(sample(arguments[0], variant), sample(arguments[1], variant));
            }
            if (raw == Optional.class) {
                return Optional.of(sample(arguments[0], variant));
            }
            return sample(raw, variant);
        }
        Class<?> raw = (Class<?>) type;
        if (raw == String.class) {
            return "v" + variant;
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return variant == 1;
        }
        if (raw == int.class || raw == Integer.class) {
            return variant + 1;
        }
        if (raw == long.class || raw == Long.class) {
            return variant + 1L;
        }
        if (raw == double.class || raw == Double.class) {
            return variant + 1.0;
        }
        if (raw == Instant.class) {
            return Instant.ofEpochSecond(variant + 1L);
        }
        if (raw == Duration.class) {
            return Duration.ofMinutes(variant + 1L);
        }
        if (raw == LocalDate.class) {
            return LocalDate.of(2026, 1, variant + 1);
        }
        if (raw == Path.class) {
            return Path.of("/v" + variant);
        }
        if (raw == URI.class) {
            return URI.create("https://example.com/v" + variant);
        }
        if (raw == OptionalLong.class) {
            return OptionalLong.of(variant + 1L);
        }
        if (raw == OptionalInt.class) {
            return OptionalInt.of(variant + 1);
        }
        if (raw.isEnum()) {
            Object[] constants = raw.getEnumConstants();
            return constants[variant % constants.length];
        }
        if (raw.isRecord()) {
            return build(raw, variant);
        }
        if (raw == List.class) {
            return List.of("v" + variant);
        }
        if (raw == Set.class) {
            return Set.of("v" + variant);
        }
        if (raw == Map.class) {
            return Map.of("v" + variant, "v" + variant);
        }
        throw new IllegalArgumentException("no sample of " + raw.getName());
    }
}
