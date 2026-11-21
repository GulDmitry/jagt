package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

record Choice(List<Branch> branches, Optional<Enum<?>> otherwise) {

  record Branch(Predicate<Active> guard, Enum<?> target) {}

  static Choice undecided() {
    return new Choice(List.of(), Optional.empty());
  }

  Enum<?> pick(Active active) {
    return branches.stream()
        .filter(branch -> branch.guard().test(active))
        .map(Branch::target)
        .findFirst()
        .orElseGet(otherwise::orElseThrow);
  }

  List<Enum<?>> targets() {
    List<Enum<?>> all = new ArrayList<>(branches.stream().map(Branch::target).toList());
    otherwise.ifPresent(all::add);
    return all;
  }
}
