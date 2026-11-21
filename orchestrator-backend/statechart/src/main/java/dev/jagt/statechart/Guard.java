package dev.jagt.statechart;

@FunctionalInterface
public interface Guard<E> {

  boolean allows(Active active, E event);
}
