package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class StatechartBuilder<E, X, F> {

  private record Pending(
      String name, Class<? extends Enum<?>> type, Enum<?> initial, Set<Enum<?>> finals) {}

  private final List<Pending> regions = new ArrayList<>();
  private final List<Datum<X>> data = new ArrayList<>();
  private final Map<String, RegionDecls<E, F>> decls = new HashMap<>();
  private final List<Transition<E, F>> transitions = new ArrayList<>();
  private final ClassDecls classes = new ClassDecls();
  private int groups;
  private int ceiling = Statechart.DEFAULT_CEILING;
  private Optional<Function<Completed, E>> completion = Optional.empty();

  StatechartBuilder() {}

  public <S extends Enum<S>> StatechartBuilder<E, X, F> region(RegionId<S> id, Set<S> finals) {
    return region(id, id.states().getEnumConstants()[0], finals);
  }

  public <S extends Enum<S>> StatechartBuilder<E, X, F> region(RegionId<S> id, S initial) {
    return region(id, initial, Set.of());
  }

  public <S extends Enum<S>> StatechartBuilder<E, X, F> region(
      RegionId<S> id, S initial, Set<S> finals) {
    regions.add(new Pending(id.name(), id.states(), initial, new LinkedHashSet<Enum<?>>(finals)));
    return this;
  }

  public <S extends Enum<S>> StatechartBuilder<E, X, F> datum(
      DataId<S> id, Function<X, S> compute) {
    data.add(new Datum<>(id.name(), compute));
    return this;
  }

  public <S extends Enum<S>> StatechartBuilder<E, X, F> onExit(RegionId<S> id, S state, F effect) {
    decl(id.name()).exit(state, effect);
    return this;
  }

  public <S extends Enum<S>> StatechartBuilder<E, X, F> onEntry(RegionId<S> id, S state, F effect) {
    decl(id.name()).entry(state, effect);
    return this;
  }

  /**
   * A do-activity as data: {@code start} is an entry effect of {@code state}, {@code stop} its exit
   * effect.
   */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> doActivity(
      RegionId<S> id, S state, F start, F stop) {
    decl(id.name()).entry(state, start);
    decl(id.name()).exit(state, stop);
    return this;
  }

  /**
   * {@code state} is a choice pseudostate: a transition reaching it continues to the branch the
   * guards pick once the transition has left its exit set, so In() reads the configuration after
   * the exits, and {@code state} the one the microstep started from; it is never active.
   */
  public <S extends Enum<S>> ChoiceBuilder<E, X, F, S> choice(RegionId<S> id, S state) {
    RegionDecls<E, F> region = decl(id.name());
    region.choice(state, Choice.undecided());
    return new ChoiceBuilder<>(this, choice -> region.choice(state, choice));
  }

  /**
   * {@code state} is a parallel state: every region in {@code regions} is entered with it, at its
   * initial state, and exited with it, and it is done when each of them is in a final state. The
   * regions are active only while it is.
   */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> parallel(
      RegionId<S> id, S state, RegionId<?>... regions) {
    decl(id.name()).parallel(state, Stream.of(regions).map(RegionId::name).toList());
    return this;
  }

  /** Entering {@code state} raises {@code event} on the internal queue, after its entry effects. */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> raiseOnEntry(
      RegionId<S> id, S state, E event) {
    decl(id.name()).entryRaise(state, event);
    return this;
  }

  /** The transition content of a compound state's initial pseudostate. */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> onInitial(
      RegionId<S> id, S compound, F effect) {
    decl(id.name()).initialEffect(compound, effect);
    return this;
  }

  /** A compound state entered by default enters {@code initial}, one of its children. */
  @SafeVarargs
  public final <S extends Enum<S>> StatechartBuilder<E, X, F> compound(
      RegionId<S> id, S parent, S initial, S... children) {
    List<Enum<?>> kids = new ArrayList<>();
    for (S child : children) {
      kids.add(child);
    }
    decl(id.name()).compound(parent, initial, kids);
    return this;
  }

  /** A compound state entered by default enters its first child in document order. */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> compound(
      RegionId<S> id, S parent, Set<S> children) {
    decl(id.name())
        .compoundByDefault(parent, children.stream().sorted().<Enum<?>>map(c -> c).toList());
    return this;
  }

  /**
   * A history pseudostate of {@code compound}: entering it through {@code toHistory} resumes what
   * was active when the compound was last exited, or {@code target}, a state inside it, when it
   * never was.
   */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> history(
      RegionId<S> id, S compound, History kind, S target) {
    decl(id.name()).history(compound, kind, target);
    return this;
  }

  /** Entering a final child raises the event the factory builds for its parent. */
  public StatechartBuilder<E, X, F> completion(Function<Completed, E> factory) {
    completion = Optional.of(factory);
    return this;
  }

  public <S extends Enum<S>> TransitionBuilder<E, X, F, S> on(
      RegionId<S> id, S from, Class<? extends E> type) {
    return new TransitionBuilder<>(
        this,
        id.name(),
        from,
        new Transition.Trigger<>(type.getSimpleName(), type::isInstance, false, Set.of(type)));
  }

  /**
   * An eventless transition, taken whenever its guard allows once the macrostep's event is spent;
   * it has no event to read, so only {@code whenActive} guards it and only {@code emit} acts.
   */
  public <S extends Enum<S>> TransitionBuilder<E, X, F, S> always(RegionId<S> id, S from) {
    return new TransitionBuilder<>(
        // Never evaluated: an eventless trigger fires on no event whatever the predicate says.
        this, id.name(), from, new Transition.Trigger<>("always", event -> false, true, Set.of()));
  }

  public <S extends Enum<S>> TransitionBuilder<E, X, F, S> onMatching(
      RegionId<S> id, S from, String label, Predicate<E> matches) {
    return new TransitionBuilder<>(
        this, id.name(), from, new Transition.Trigger<>(label, matches, false, Set.of()));
  }

  /**
   * As above, for a move of {@code kind}: where the chart declares {@link #moves}, only a
   * transition naming its kind counts as taking it.
   */
  public <S extends Enum<S>> TransitionBuilder<E, X, F, S> onMatching(
      RegionId<S> id, S from, Class<? extends E> kind, String label, Predicate<E> matches) {
    return new TransitionBuilder<>(
        this, id.name(), from, new Transition.Trigger<>(label, matches, false, Set.of(kind)));
  }

  /**
   * One transition declared from every state in {@code from}, drawn as a single labelled transition
   * from the set.
   */
  public <S extends Enum<S>> TransitionSetBuilder<E, X, F, S> onMatching(
      RegionId<S> id, Set<S> from, String label, Predicate<E> matches) {
    int group = ++groups;
    return new TransitionSetBuilder<>(
        this,
        from.stream()
            .sorted()
            .map(state -> onMatching(id, state, label, matches).inGroup(group))
            .toList());
  }

  /** As above, for a move of {@code kind}. */
  public <S extends Enum<S>> TransitionSetBuilder<E, X, F, S> onMatching(
      RegionId<S> id, Set<S> from, Class<? extends E> kind, String label, Predicate<E> matches) {
    int group = ++groups;
    return new TransitionSetBuilder<>(
        this,
        from.stream()
            .sorted()
            .map(state -> onMatching(id, state, kind, label, matches).inGroup(group))
            .toList());
  }

  /**
   * As above, for a predicate that takes several kinds of move; a kind that is not a leaf of {@link
   * #moves} stands for each leaf under it.
   */
  public <S extends Enum<S>> TransitionBuilder<E, X, F, S> onMatchingAny(
      RegionId<S> id, S from, Set<Class<? extends E>> kinds, String label, Predicate<E> matches) {
    return new TransitionBuilder<>(
        this,
        id.name(),
        from,
        new Transition.Trigger<>(label, matches, false, Set.<Class<?>>copyOf(kinds)));
  }

  /**
   * Every leaf of {@code root} is a kind of move the chart classifies: per region it is addressed
   * to ({@link #addressed}), each state either has an edge for it or is declared {@link #ignores}
   * or {@link #refuses}; {@link #build} fails on a state with none.
   */
  public StatechartBuilder<E, X, F> moves(Class<? extends E> root) {
    classes.moves(root);
    return this;
  }

  /** {@code kind} is the concern of {@code regions}, and of no other region. */
  public StatechartBuilder<E, X, F> addressed(Class<? extends E> kind, RegionId<?>... regions) {
    classes.addressed(kind, Stream.of(regions).map(RegionId::name).toList());
    return this;
  }

  /** {@code kind} changes nothing in {@code states} of {@code id}, for {@code why}. */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> ignores(
      RegionId<S> id, Class<? extends E> kind, Set<S> states, String why) {
    classes.declare(id.name(), kind, states, false, why);
    return this;
  }

  /** {@code kind} is turned away in {@code states} of {@code id}, under the name {@code named}. */
  public <S extends Enum<S>> StatechartBuilder<E, X, F> refuses(
      RegionId<S> id, Class<? extends E> kind, Set<S> states, String named) {
    classes.declare(id.name(), kind, states, true, named);
    return this;
  }

  /**
   * At most {@code microsteps} follow-up microsteps, internal events and eventless, per macrostep.
   */
  public StatechartBuilder<E, X, F> raiseCeiling(int microsteps) {
    ceiling = microsteps;
    return this;
  }

  public Statechart<E, X, F> build() {
    List<Region<E, F>> built =
        regions.stream()
            .map(region ->
                new Region<>(
                    region.name(),
                    region.type(),
                    region.initial(),
                    region.finals(),
                    Map.copyOf(decl(region.name()).exits()),
                    Map.copyOf(decl(region.name()).entries()),
                    Map.copyOf(decl(region.name()).entryRaises()),
                    decl(region.name()).tree()))
            .toList();
    List<String> names = new ArrayList<>(regions.stream().map(Pending::name).toList());
    data.forEach(datum -> names.add(datum.name()));
    List<String> problems = Validation.problems(names, built, data, transitions);
    Classes<E, F> classed = classes.of(built, transitions);
    problems.addAll(classed.problems());
    if (!problems.isEmpty()) {
      throw new StatechartInvalid(problems);
    }
    return new Statechart<>(
        built, List.copyOf(data), List.copyOf(transitions), ceiling, completion, classed);
  }

  private RegionDecls<E, F> decl(String region) {
    return decls.computeIfAbsent(region, name -> new RegionDecls<>());
  }

  void add(Transition<E, F> transition) {
    transitions.add(transition);
  }
}
