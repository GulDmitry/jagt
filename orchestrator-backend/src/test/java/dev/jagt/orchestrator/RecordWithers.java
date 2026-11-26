package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import dev.jagt.orchestrator.port.CurrentOrigin;
import dev.jagt.orchestrator.port.GitDistrust;
import dev.jagt.orchestrator.port.MergedRefresh;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
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
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class RecordWithers {

  private static final List<Class<?>> RECORDS =
      SourceRoots.ALL.stream()
          .filter(JavaClass::isRecord)
          .<Class<?>>map(RecordWithers::load)
          .toList();

  private RecordWithers() {}

  private static Class<?> load(JavaClass type) {
    try {
      return Class.forName(type.getName(), false, SourceRoots.LOADER);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Wither(Class<?> type, RecordComponent component, Method method) {}

  public static Stream<Wither> all() {
    return RECORDS.stream()
        .flatMap(type ->
            Arrays.stream(type.getRecordComponents())
                .flatMap(component ->
                    witherOf(type, component).stream()
                        .map(method -> new Wither(type, component, method))));
  }

  private static Optional<Method> witherOf(Class<?> type, RecordComponent component) {
    String name =
        "with"
            + Character.toUpperCase(component.getName().charAt(0))
            + component.getName().substring(1);
    try {
      Method method = type.getMethod(name, component.getType());
      return method.getReturnType() == type ? Optional.of(method) : Optional.empty();
    } catch (NoSuchMethodException e) {
      return Optional.empty();
    }
  }

  public static String defect(Wither wither) {
    String at = wither.type().getName() + "." + wither.method().getName();
    try {
      Object before = build(wither.type(), 0);
      Object changed = sample(wither.component().getGenericType(), 1);
      Object after = wither.method().invoke(before, changed);
      return Arrays.stream(wither.type().getRecordComponents())
          .filter(other ->
              !equal(
                  value(other, after),
                  other.getName().equals(wither.component().getName())
                      ? changed
                      : value(other, before)))
          .map(other -> at + " left " + other.getName() + " wrong")
          .findFirst()
          .orElse("");
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
    Constructor<?> canonical =
        type.getDeclaredConstructor(
            Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
    canonical.setAccessible(true);
    Object[] values = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      values[i] = sample(components[i].getGenericType(), variant);
    }
    return canonical.newInstance(values);
  }

  private static final Map<Class<?>, IntFunction<Object>> SAMPLES =
      Map.ofEntries(
          Map.entry(String.class, variant -> "v" + variant),
          Map.entry(boolean.class, variant -> variant == 1),
          Map.entry(Boolean.class, variant -> variant == 1),
          Map.entry(int.class, variant -> variant + 1),
          Map.entry(Integer.class, variant -> variant + 1),
          Map.entry(long.class, variant -> variant + 1L),
          Map.entry(Long.class, variant -> variant + 1L),
          Map.entry(double.class, variant -> variant + 1.0),
          Map.entry(Double.class, variant -> variant + 1.0),
          Map.entry(Instant.class, variant -> Instant.ofEpochSecond(variant + 1L)),
          Map.entry(Duration.class, variant -> Duration.ofMinutes(variant + 1L)),
          Map.entry(LocalDate.class, variant -> LocalDate.of(2026, 1, variant + 1)),
          Map.entry(Path.class, variant -> Path.of("/v" + variant)),
          Map.entry(URI.class, variant -> URI.create("https://example.com/v" + variant)),
          Map.entry(OptionalLong.class, variant -> OptionalLong.of(variant + 1L)),
          Map.entry(OptionalInt.class, variant -> OptionalInt.of(variant + 1)),
          Map.entry(BooleanSupplier.class, variant -> (BooleanSupplier) () -> variant == 0),
          Map.entry(GitDistrust.class, variant -> (GitDistrust) id -> Optional.of("v" + variant)),
          Map.entry(MergedRefresh.class, variant -> (MergedRefresh) id -> variant == 0),
          Map.entry(
              CurrentOrigin.class,
              variant ->
                  (CurrentOrigin) () -> variant == 0 ? ActionOrigin.BOARD : ActionOrigin.MASTER),
          Map.entry(
              Press.class, variant -> variant == 0 ? Press.NONE : new Press.Move("v" + variant)),
          Map.entry(List.class, variant -> List.of("v" + variant)),
          Map.entry(Set.class, variant -> Set.of("v" + variant)),
          Map.entry(Map.class, variant -> Map.of("v" + variant, "v" + variant)));

  private static Object sample(Type type, int variant) throws ReflectiveOperationException {
    if (type instanceof ParameterizedType parameterized) {
      return sampleOfGeneric(parameterized, variant);
    }
    Class<?> raw = (Class<?>) type;
    IntFunction<Object> known = SAMPLES.get(raw);
    if (known != null) {
      return known.apply(variant);
    }
    if (raw.isEnum()) {
      Object[] constants = raw.getEnumConstants();
      return constants[variant % constants.length];
    }
    if (raw.isRecord()) {
      return build(raw, variant);
    }
    throw new IllegalArgumentException("no sample of " + raw.getName());
  }

  private static Object sampleOfGeneric(ParameterizedType parameterized, int variant)
      throws ReflectiveOperationException {
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
    if (raw == Supplier.class) {
      Object value = sample(arguments[0], variant);
      return (Supplier<Object>) () -> value;
    }
    return sample(raw, variant);
  }
}
