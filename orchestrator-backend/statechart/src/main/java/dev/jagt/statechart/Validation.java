package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class Validation {

  private Validation() {}

  static <E, X, F> List<String> problems(
      List<String> names,
      List<Region<E, F>> regions,
      List<Datum<X>> data,
      List<Transition<E, F>> transitions) {
    List<String> problems = new ArrayList<>();
    names.stream()
        .distinct()
        .filter(name -> Collections.frequency(names, name) > 1)
        .forEach(name -> problems.add("region " + name + " is declared more than once"));
    Set<String> regionNames = new LinkedHashSet<>(regions.stream().map(Region::name).toList());
    Set<String> datumNames = new LinkedHashSet<>(data.stream().map(Datum::name).toList());
    transitions.stream()
        .map(Transition::region)
        .distinct()
        .filter(name -> !regionNames.contains(name))
        .forEach(name ->
            problems.add(
                datumNames.contains(name)
                    ? "datum " + name + " has a transition"
                    : "transition on unknown region " + name));
    regions.forEach(region -> problems.addAll(problemsOf(region, transitions)));
    problems.addAll(parallelProblems(regions));
    return problems;
  }

  private static <E, F> List<String> parallelProblems(List<Region<E, F>> regions) {
    List<String> problems = new ArrayList<>();
    Set<String> known = new LinkedHashSet<>(regions.stream().map(Region::name).toList());
    List<String> claimed = new ArrayList<>();
    for (Region<E, F> region : regions) {
      Tree<F> tree = region.tree();
      tree.parallels()
          .forEach((state, owned) -> {
            String where = region.name() + "." + state.name();
            if (owned.isEmpty()) {
              problems.add("parallel " + where + " has no region");
            }
            if (tree.isCompound(state) || tree.isChoice(state) || region.finals().contains(state)) {
              problems.add("parallel " + where + " is compound, a choice or final");
            }
            owned.stream()
                .filter(name -> !known.contains(name))
                .forEach(name -> problems.add("parallel " + where + " has unknown region " + name));
            claimed.addAll(owned);
          });
    }
    claimed.stream()
        .distinct()
        .filter(name -> Collections.frequency(claimed, name) > 1)
        .forEach(name -> problems.add("region " + name + " belongs to more than one parallel"));
    regions.stream()
        .filter(region -> ownsItself(regions, region.name()))
        .forEach(region -> problems.add("region " + region.name() + " is inside itself"));
    return problems;
  }

  private static <E, F> boolean ownsItself(List<Region<E, F>> regions, String start) {
    Set<String> seen = new LinkedHashSet<>();
    List<String> frontier = new ArrayList<>(List.of(start));
    while (!frontier.isEmpty()) {
      String next = frontier.removeFirst();
      regions.stream()
          .filter(region -> region.name().equals(next))
          .flatMap(region -> region.tree().parallels().values().stream())
          .flatMap(List::stream)
          .filter(seen::add)
          .forEach(frontier::add);
    }
    return seen.contains(start);
  }

  private static <E, F> List<String> problemsOf(
      Region<E, F> region, List<Transition<E, F>> transitions) {
    List<Transition<E, F>> own =
        transitions.stream().filter(t -> t.region().equals(region.name())).toList();
    Set<Enum<?>> reached = reachable(region, own);
    List<String> problems = new ArrayList<>(treeProblems(region));
    problems.addAll(historyProblems(region, own));
    problems.addAll(choiceProblems(region, own));
    region.states().stream()
        .flatMap(state ->
            Stream.of(
                leafWithoutExit(region, own, state)
                    ? "state " + region.name() + "." + state.name() + " has no way out"
                    : "",
                reached.contains(state)
                    ? ""
                    : "state " + region.name() + "." + state.name() + " is unreachable"))
        .filter(problem -> !problem.isEmpty())
        .forEach(problems::add);
    return problems;
  }

  private static <E, F> boolean leafWithoutExit(
      Region<E, F> region, List<Transition<E, F>> own, Enum<?> state) {
    return !region.finals().contains(state)
        && !region.tree().isCompound(state)
        && !region.tree().isChoice(state)
        && region.tree().lineage(state).stream()
            .noneMatch(level -> own.stream().anyMatch(t -> t.source().equals(level)));
  }

  private static <E, F> List<String> treeProblems(Region<E, F> region) {
    Tree<F> tree = region.tree();
    List<String> problems = new ArrayList<>();
    tree.initials()
        .forEach((parent, initial) -> {
          if (!tree.kids(parent).contains(initial)) {
            problems.add(
                "compound "
                    + region.name()
                    + "."
                    + parent.name()
                    + " has initial "
                    + initial.name()
                    + " that is not its child");
          }
        });
    tree.children().values().stream()
        .flatMap(List::stream)
        .distinct()
        .filter(
            kid -> tree.children().values().stream().filter(kids -> kids.contains(kid)).count() > 1)
        .forEach(kid ->
            problems.add(
                "state " + region.name() + "." + kid.name() + " has more than one parent"));
    region.states().stream()
        .filter(tree::cyclic)
        .forEach(state ->
            problems.add("state " + region.name() + "." + state.name() + " is its own ancestor"));
    return problems;
  }

  private static <E, F> List<String> historyProblems(
      Region<E, F> region, List<Transition<E, F>> own) {
    Tree<F> tree = region.tree();
    List<String> problems = new ArrayList<>();
    tree.histories().values().stream()
        .flatMap(List::stream)
        .forEach(history -> {
          String where = region.name() + "." + history.compound().name();
          if (!tree.isCompound(history.compound())) {
            problems.add("history " + where + " is not on a compound state");
          } else if (!tree.inside(history.target(), history.compound())) {
            problems.add(
                "history "
                    + where
                    + " has default "
                    + history.target().name()
                    + " outside its compound");
          }
        });
    tree.histories()
        .forEach((compound, declared) -> {
          if (declared.stream().map(HistoryState::kind).distinct().count() < declared.size()) {
            problems.add(
                "history " + region.name() + "." + compound.name() + " is declared more than once");
          }
        });
    own.stream()
        .filter(t -> t.goal().via().isPresent())
        .filter(t -> tree.historyState(t.target(), t.goal().via().get()).isEmpty())
        .forEach(t -> problems.add("transition " + t.describe() + " enters an undeclared history"));
    return problems;
  }

  private static <E, F> List<String> choiceProblems(
      Region<E, F> region, List<Transition<E, F>> own) {
    Tree<F> tree = region.tree();
    List<String> problems = new ArrayList<>();
    tree.choices()
        .forEach((state, choice) -> {
          String where = region.name() + "." + state.name();
          if (choice.otherwise().isEmpty()) {
            problems.add("choice " + where + " has no otherwise");
          }
          if (own.stream().anyMatch(t -> t.source().equals(state))) {
            problems.add("choice " + where + " has a transition");
          }
          if (region.finals().contains(state)) {
            problems.add("choice " + where + " is final");
          }
          if (choiceCycle(tree, state)) {
            problems.add("choice " + where + " leads back to itself");
          }
        });
    if (tree.isChoice(region.startLeaf())) {
      problems.add("choice " + region.name() + "." + region.startLeaf().name() + " is initial");
    }
    tree.histories().values().stream()
        .flatMap(List::stream)
        .filter(history -> tree.isChoice(tree.leaf(history.target())))
        .forEach(history ->
            problems.add(
                "choice "
                    + region.name()
                    + "."
                    + tree.leaf(history.target()).name()
                    + " is the default of a history"));
    return problems;
  }

  private static <F> boolean choiceCycle(Tree<F> tree, Enum<?> start) {
    Set<Enum<?>> seen = new LinkedHashSet<>();
    List<Enum<?>> frontier = new ArrayList<>(List.of(start));
    while (!frontier.isEmpty()) {
      Enum<?> next = frontier.removeFirst();
      if (tree.isChoice(next)) {
        tree.choice(next).targets().stream()
            .map(tree::leaf)
            .forEach(leaf -> {
              if (seen.add(leaf)) {
                frontier.add(leaf);
              }
            });
      }
    }
    return seen.contains(start);
  }

  private static <E, F> Set<Enum<?>> reachable(Region<E, F> region, List<Transition<E, F>> own) {
    Set<Enum<?>> reached = new LinkedHashSet<>(region.tree().entered(region.initial()));
    List<Enum<?>> frontier = new ArrayList<>(reached);
    while (!frontier.isEmpty()) {
      Enum<?> state = frontier.removeFirst();
      own.stream()
          .filter(t -> t.source().equals(state))
          .flatMap(t -> targets(region, t).stream())
          .filter(reached::add)
          .forEach(frontier::add);
      if (region.tree().isChoice(state)) {
        region.tree().choice(state).targets().stream()
            .flatMap(target -> region.tree().entered(target).stream())
            .filter(reached::add)
            .forEach(frontier::add);
      }
    }
    return reached;
  }

  private static <E, F> List<Enum<?>> targets(Region<E, F> region, Transition<E, F> t) {
    List<Enum<?>> reached = new ArrayList<>(region.tree().entered(t.target()));
    if (t.goal().via().isPresent()) {
      region.states().stream()
          .filter(state -> region.tree().inside(state, t.target()))
          .forEach(reached::add);
    }
    return reached;
  }
}
