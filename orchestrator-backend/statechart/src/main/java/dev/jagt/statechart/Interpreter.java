package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One macrostep: the event's microstep, then eventless transitions and internal events until
 * stable.
 */
final class Interpreter<E, X, F> {

  private record Selected<E, F>(List<Taken<E, F>> taken, Optional<E> event) {}

  private record Exited<E, F>(Region<E, F> region, Enum<?> state, Enum<?> leaf) {}

  private final Statechart<E, X, F> chart;
  private final X dataModel;
  private final Map<String, Enum<?>> computed = new HashMap<>();
  private final Run<E, F> run;

  Interpreter(Statechart<E, X, F> chart, Configuration start, X dataModel) {
    this.chart = chart;
    this.dataModel = dataModel;
    this.run = new Run<>(start);
  }

  Macrostep<F> run(E external) {
    Optional<E> given = Optional.of(external);
    List<Taken<E, F>> first = enabled(given);
    if (!first.isEmpty()) {
      microstep(new Selected<>(first, given));
    }
    Optional<Selected<E, F>> next = next();
    while (next.isPresent()) {
      microstep(next.get());
      if (++run.followUps > chart.ceiling()) {
        throw new RaiseCeilingExceeded(chart.ceiling(), run.moves);
      }
      next = next();
    }
    return new Macrostep<>(run.config, List.copyOf(run.effects), chart.complete(run.config));
  }

  private Optional<Selected<E, F>> next() {
    List<Taken<E, F>> taken = enabled(Optional.empty());
    Optional<E> event = Optional.empty();
    while (taken.isEmpty() && !run.queue.isEmpty()) {
      event = Optional.of(run.queue.remove());
      taken = enabled(event);
    }
    return taken.isEmpty() ? Optional.empty() : Optional.of(new Selected<>(taken, event));
  }

  private List<Taken<E, F>> enabled(Optional<E> event) {
    List<Taken<E, F>> taken = new ArrayList<>();
    for (Region<E, F> region : chart.regions()) {
      Active active = chart.active(run.config, dataModel, computed);
      run.config
          .find(region.name())
          .ifPresent(leaf ->
              chart
                  .select(region, leaf, active, event)
                  .map(t -> Taken.of(region, t, leaf))
                  .ifPresent(taken::add));
    }
    return withoutPreempted(taken);
  }

  private List<Taken<E, F>> withoutPreempted(List<Taken<E, F>> taken) {
    Set<String> acting = new HashSet<>();
    taken.forEach(t -> acting.add(t.region().name()));
    return taken.stream()
        .filter(t ->
            t.move().exits().stream()
                .flatMap(state -> chart.regionsUnder(t.region(), state).stream())
                .noneMatch(acting::contains))
        .toList();
  }

  private Taken<E, F> resolved(Taken<E, F> taken, Active active) {
    Tree<F> tree = taken.region().tree();
    Taken<E, F> current = taken;
    for (Optional<Enum<?>> ahead = current.choiceAhead();
        ahead.isPresent();
        ahead = current.choiceAhead()) {
      current = current.rerouted(tree.choice(ahead.get()).pick(active));
    }
    return current;
  }

  private List<Exited<E, F>> exited(List<Taken<E, F>> taken) {
    List<Exited<E, F>> all = new ArrayList<>();
    for (Taken<E, F> t : taken) {
      Enum<?> leaf = run.config.find(t.region().name()).orElseThrow();
      for (Enum<?> state : t.move().exits()) {
        exitedRegionsOf(t.region(), state, all);
        all.add(new Exited<>(t.region(), state, leaf));
      }
    }
    return all;
  }

  private void exitedRegionsOf(Region<E, F> region, Enum<?> state, List<Exited<E, F>> all) {
    for (String name : region.tree().regionsOf(state).reversed()) {
      Region<E, F> sub = chart.byName(name);
      Enum<?> leaf = run.config.find(name).orElseThrow();
      for (Enum<?> inner : sub.tree().lineage(leaf)) {
        exitedRegionsOf(sub, inner, all);
        all.add(new Exited<>(sub, inner, leaf));
      }
    }
  }

  private Active afterExits(List<Exited<E, F>> exited) {
    Map<String, Set<Enum<?>>> gone = new HashMap<>();
    exited.forEach(
        x -> gone.computeIfAbsent(x.region().name(), name -> new HashSet<>()).add(x.state()));
    return chart.active(run.config, dataModel, computed, gone);
  }

  private void microstep(Selected<E, F> selected) {
    List<Exited<E, F>> exited = exited(selected.taken());
    exited.forEach(x -> run.effects.addAll(x.region().exitEffects(x.state())));
    for (Taken<E, F> t : selected.taken()) {
      run.effects.addAll(t.transition().effectsFor(selected.event()));
      run.queue.addAll(t.transition().response().raises());
    }
    Active active = afterExits(exited);
    List<Taken<E, F>> resolved = selected.taken().stream().map(t -> resolved(t, active)).toList();
    resolved.forEach(t -> run.moves.add(t.transition().describe()));
    Set<Completed> doneBefore = chart.doneParallels(run.config);
    boolean wasComplete = chart.complete(run.config);
    leave(exited, selected.taken());
    resolved.forEach(this::enter);
    raiseCompletions(doneBefore, wasComplete);
  }

  private void leave(List<Exited<E, F>> exited, List<Taken<E, F>> taken) {
    Set<String> acting = new HashSet<>();
    taken.forEach(t -> acting.add(t.region().name()));
    for (Exited<E, F> x : exited) {
      String name = x.region().name();
      x.region()
          .tree()
          .historiesOf(x.state())
          .forEach(history -> run.config = run.config.remembering(history.key(name), x.leaf()));
      if (!acting.contains(name)) {
        run.config = run.config.without(name);
      }
    }
  }

  private void raiseCompletions(Set<Completed> doneBefore, boolean wasComplete) {
    chart
        .completion()
        .ifPresent(factory -> {
          chart.doneParallels(run.config).stream()
              .filter(done -> !doneBefore.contains(done))
              .forEach(done -> run.queue.add(factory.apply(done)));
          if (!wasComplete && chart.complete(run.config)) {
            run.queue.add(factory.apply(Completed.ofChart()));
          }
        });
  }

  private void enter(Taken<E, F> taken) {
    Traversal move = taken.entering(run.config);
    run.config = run.config.with(taken.region().name(), move.leaf());
    enterAll(taken.region(), move.entries());
  }

  private void enterAll(Region<E, F> region, List<Traversal.Entry> entries) {
    for (Traversal.Entry entry : entries) {
      run.effects.addAll(region.entryEffects(entry));
      run.queue.addAll(region.raisesOnEntry(entry.state()));
      completed(region, entry.state()).ifPresent(run.queue::add);
      for (String name : region.tree().regionsOf(entry.state())) {
        Region<E, F> sub = chart.byName(name);
        run.config = run.config.with(name, sub.startLeaf());
        enterAll(sub, sub.startEntries());
      }
    }
  }

  private Optional<E> completed(Region<E, F> region, Enum<?> entered) {
    return chart
        .completion()
        .filter(factory -> region.finals().contains(entered))
        .flatMap(factory ->
            region
                .tree()
                .parent(entered)
                .map(parent -> factory.apply(new Completed(region.name(), parent.name()))));
  }
}
