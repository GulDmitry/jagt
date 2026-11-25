package dev.jagt.orchestrator.service;

/**
 * Where a round's threads were read: a hosted request's notes, or the reviewer's read of a tree.
 */
public enum RoundSource {
  REQUEST,
  TREE
}
