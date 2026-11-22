package dev.jagt.gradle.testsfor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class Selection {

  final Set<String> projects = new LinkedHashSet<>();
  final Set<String> otherSets = new LinkedHashSet<>();
  final Set<String> tests = new LinkedHashSet<>();
  final Set<String> rules = new LinkedHashSet<>();
  boolean removedMain;
  boolean board;
  boolean fitness;
  boolean buildLogic;

  void rules(String... names) {
    rules.addAll(Arrays.asList(names));
  }
}
