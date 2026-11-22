package dev.jagt.statechart;

/** What a history pseudostate remembers of its compound state: its direct child or its leaf. */
public enum History {
  SHALLOW,
  DEEP
}
