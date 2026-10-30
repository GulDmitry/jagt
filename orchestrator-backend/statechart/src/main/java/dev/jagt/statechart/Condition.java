package dev.jagt.statechart;

@FunctionalInterface
public interface Condition<E> {

    boolean allows(Others others, E event);
}
