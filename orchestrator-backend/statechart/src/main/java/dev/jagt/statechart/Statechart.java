package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Regions run in parallel and a macrostep is pure. Within a microstep every region picks the
 * enabled transition of its innermost state, the first in document order among equals, against the
 * configuration from before it; exits, children first, come before transition effects and entries,
 * parents first, after them; then eventless transitions run, then raised events in raise order,
 * until none is enabled. Every region final raises the chart's own completion and halts it. A
 * transition to its own state still exits it; an internal one does not, and one to a descendant of
 * its compound source leaves the source entered. A parallel state is entered and exited with its
 * regions, which are active only while it is; a transition of the parallel state is dropped when a
 * region of it has one enabled. A datum is computed from the data model the caller passes only when
 * a guard first reads it, and once for the whole macrostep.
 */
public final class Statechart<E, X, F> {

  static final int DEFAULT_CEILING = 100;

  private final List<Region<E, F>> regions;
  private final List<Datum<X>> data;
  private final List<Transition<E, F>> transitions;
  private final int ceiling;
  private final Optional<Function<Completed, E>> completion;

  Statechart(
      List<Region<E, F>> regions,
      List<Datum<X>> data,
      List<Transition<E, F>> transitions,
      int ceiling,
      Optional<Function<Completed, E>> completion) {
    this.completion = completion;
    this.regions = regions;
    this.data = data;
    this.transitions = transitions;
    this.ceiling = ceiling;
  }

  public static <E, X, F> StatechartBuilder<E, X, F> builder() {
    return new StatechartBuilder<>();
  }

  public Configuration initial() {
    Map<String, Enum<?>> states = new LinkedHashMap<>();
    regions.stream()
        .filter(region -> enclosing(region.name()).isEmpty())
        .forEach(r -> start(r, states));
    return new Configuration(states, Map.of());
  }

  private void start(Region<E, F> region, Map<String, Enum<?>> states) {
    Enum<?> leaf = region.startLeaf();
    states.put(region.name(), leaf);
    region.tree().regionsOf(leaf).forEach(name -> start(byName(name), states));
  }

  public Configuration configuration(Map<String, String> plain) {
    return configuration(plain, Map.of());
  }

  public Configuration configuration(Map<String, String> plain, Map<String, String> history) {
    List<String> problems = new ArrayList<>();
    plain.keySet().stream()
        .filter(name -> regions.stream().noneMatch(region -> region.name().equals(name)))
        .forEach(name -> problems.add("no region " + name));
    Map<String, Enum<?>> states = new LinkedHashMap<>();
    for (Region<E, F> region : regions) {
      if (!isActive(region.name(), plain)) {
        if (plain.containsKey(region.name())) {
          problems.add("region " + region.name() + " is not active");
        }
        continue;
      }
      Optional<Enum<?>> state =
          Optional.ofNullable(plain.get(region.name())).flatMap(name -> stable(region, name));
      state.ifPresentOrElse(
          s -> states.put(region.name(), s),
          () ->
              problems.add(
                  "region "
                      + region.name()
                      + " has no known state, got "
                      + plain.get(region.name())));
    }
    Map<String, Enum<?>> remembered = remembered(history, problems);
    if (!problems.isEmpty()) {
      throw new InvalidConfiguration(problems);
    }
    return new Configuration(states, remembered);
  }

  private Optional<Enum<?>> stable(Region<E, F> region, String name) {
    return region.states().stream()
        .filter(s ->
            s.name().equals(name) && !region.tree().isCompound(s) && !region.tree().isChoice(s))
        .<Enum<?>>map(s -> s)
        .findFirst();
  }

  private boolean isActive(String name, Map<String, String> plain) {
    return enclosing(name)
        .map(o -> o.state().name().equals(plain.get(o.region())) && isActive(o.region(), plain))
        .orElse(true);
  }

  private Map<String, Enum<?>> remembered(Map<String, String> history, List<String> problems) {
    Map<String, Enum<?>> remembered = new LinkedHashMap<>();
    history.forEach((key, name) ->
        regions.stream()
            .map(region -> region.remembered(key, name))
            .flatMap(Optional::stream)
            .findFirst()
            .ifPresentOrElse(
                leaf -> remembered.put(key, leaf),
                () -> problems.add("history " + key + " has no known leaf, got " + name)));
    return remembered;
  }

  public Macrostep<F> step(Configuration start, X dataModel, E event) {
    return new Interpreter<>(this, start, dataModel).run(event);
  }

  /**
   * Whether a transition leaving {@code region} takes {@code event} in {@code start}; nothing moves
   * and nothing is emitted.
   */
  public <S extends Enum<S>> boolean takes(
      RegionId<S> region, Configuration start, X dataModel, E event) {
    Map<String, Enum<?>> computed = new HashMap<>();
    Active active = active(start, dataModel, computed);
    Enum<?> state =
        start
            .find(region.name())
            .orElseThrow(() -> new IllegalArgumentException("no region " + region.name()));
    return regions.stream()
        .filter(r -> r.name().equals(region.name()))
        .anyMatch(r -> select(r, state, active, Optional.of(event)).isPresent());
  }

  public String render() {
    return Mermaid.render(regions, data, transitions);
  }

  Optional<Transition<E, F>> select(
      Region<E, F> region, Enum<?> leaf, Active active, Optional<E> event) {
    return region.tree().lineage(leaf).stream()
        .map(level ->
            transitions.stream()
                .filter(t -> t.region().equals(region.name()) && t.takes(level, event, active))
                .findFirst())
        .flatMap(Optional::stream)
        .findFirst();
  }

  boolean complete(Configuration config) {
    return regions.stream()
        .filter(region -> enclosing(region.name()).isEmpty())
        .allMatch(region -> region.finals().contains(config.find(region.name()).orElseThrow()));
  }

  Set<Completed> doneParallels(Configuration config) {
    Set<Completed> done = new LinkedHashSet<>();
    for (Region<E, F> region : regions) {
      Optional<Enum<?>> leaf = config.find(region.name());
      List<String> owned = leaf.map(region.tree()::regionsOf).orElse(List.of());
      if (!owned.isEmpty() && owned.stream().allMatch(name -> inFinal(config, name))) {
        done.add(new Completed.OfState(region.name(), leaf.orElseThrow().name()));
      }
    }
    return done;
  }

  private boolean inFinal(Configuration config, String name) {
    return config.find(name).filter(byName(name).finals()::contains).isPresent();
  }

  Set<String> regionsUnder(Region<E, F> region, Enum<?> state) {
    Set<String> under = new LinkedHashSet<>();
    for (String name : region.tree().regionsOf(state)) {
      Region<E, F> sub = byName(name);
      under.add(name);
      sub.states().forEach(inner -> under.addAll(regionsUnder(sub, inner)));
    }
    return under;
  }

  Region<E, F> byName(String name) {
    return regions.stream()
        .filter(region -> region.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("no region " + name));
  }

  private record Enclosing(String region, Enum<?> state) {}

  private Optional<Enclosing> enclosing(String name) {
    return regions.stream()
        .flatMap(region ->
            region.tree().parallels().entrySet().stream()
                .filter(entry -> entry.getValue().contains(name))
                .map(entry -> new Enclosing(region.name(), entry.getKey())))
        .findFirst();
  }

  List<Region<E, F>> regions() {
    return regions;
  }

  int ceiling() {
    return ceiling;
  }

  Optional<Function<Completed, E>> completion() {
    return completion;
  }

  Active active(Configuration config, X dataModel, Map<String, Enum<?>> computed) {
    return active(config, dataModel, computed, Map.of());
  }

  Active active(
      Configuration config,
      X dataModel,
      Map<String, Enum<?>> computed,
      Map<String, Set<Enum<?>>> exited) {
    return new Active(
        name ->
            config.find(name).orElseThrow(() -> new IllegalArgumentException("no region " + name)),
        (name, state) ->
            !exited.getOrDefault(name, Set.of()).contains(state) && within(config, name, state),
        name -> datum(dataModel, name, computed));
  }

  private boolean within(Configuration config, String name, Enum<?> state) {
    Region<E, F> region = byName(name);
    return config
        .find(name)
        .filter(leaf -> region.tree().lineage(leaf).contains(state))
        .isPresent();
  }

  private Enum<?> datum(X dataModel, String name, Map<String, Enum<?>> computed) {
    return computed.computeIfAbsent(
        name,
        key ->
            data.stream()
                .filter(d -> d.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no datum " + key))
                .compute()
                .apply(dataModel));
  }
}
