package dev.jagt.gradle.testsfor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class Selection {

  final Set<String> projects = new LinkedHashSet<>();
  final Set<String> otherSets = new LinkedHashSet<>();
  final Set<String> tests = new LinkedHashSet<>();
  final Set<String> properties = new LinkedHashSet<>();
  final Set<String> rules = new LinkedHashSet<>();
  final Set<String> roleEvals = new LinkedHashSet<>();
  boolean removedMain;
  boolean board;
  boolean fitness;
  boolean buildLogic;
  boolean sizeRows;

  void rules(String... names) {
    rules.addAll(Arrays.asList(names));
  }
}
