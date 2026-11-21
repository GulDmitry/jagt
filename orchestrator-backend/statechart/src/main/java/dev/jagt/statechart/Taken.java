package dev.jagt.statechart;

import java.util.Optional;

record Taken<E, F>(Region<E, F> region, Transition<E, F> transition, Traversal move) {

  static <E, F> Taken<E, F> of(Region<E, F> region, Transition<E, F> transition, Enum<?> leaf) {
    Tree<F> tree = region.tree();
    Enum<?> source = transition.source();
    Enum<?> target = transition.target();
    if (!transition.internal()) {
      return new Taken<>(region, transition, tree.move(source, target, leaf));
    }
    if (target.equals(source)) {
      return new Taken<>(region, transition, Traversal.none(leaf));
    }
    Traversal move =
        tree.isCompound(source) && tree.ancestors(target).contains(source)
            ? tree.moveWithin(source, target, leaf)
            : tree.move(source, target, leaf);
    return new Taken<>(region, transition, move);
  }

  Optional<Enum<?>> choiceAhead() {
    Enum<?> leaf = region.tree().leaf(transition.target());
    return region.tree().isChoice(leaf) && transition.goal().via().isEmpty()
        ? Optional.of(leaf)
        : Optional.empty();
  }

  Taken<E, F> rerouted(Enum<?> target) {
    Tree<F> tree = region.tree();
    Enum<?> choice = tree.leaf(transition.target());
    Traversal onward = tree.move(choice, target, choice);
    return new Taken<>(
        region,
        transition.retargeted(target),
        new Traversal(move.exits(), onward.entries(), onward.leaf()));
  }

  Traversal entering(Configuration config) {
    return transition
        .goal()
        .via()
        .flatMap(kind -> region.tree().historyState(transition.target(), kind))
        .map(history ->
            region.tree().restore(move, history, config.stored(history.key(region.name()))))
        .orElse(move);
  }
}
