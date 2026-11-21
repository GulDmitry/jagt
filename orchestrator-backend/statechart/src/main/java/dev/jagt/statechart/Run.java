package dev.jagt.statechart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Run<E, F> {

  Configuration config;
  final List<F> effects = new ArrayList<>();
  final List<String> moves = new ArrayList<>();
  final Deque<E> queue = new ArrayDeque<>();
  int followUps;

  Run(Configuration start) {
    this.config = start;
  }
}
