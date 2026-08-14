package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.ActionOrigin;

import java.util.function.Supplier;

/**
 * Carries "who asked" from an entry point down to the write, because nothing in between can be asked: a deploy
 * reaches the same code whether a human clicked it, typed it, or an agent called the tool, and threading a
 * parameter through would put that question into every signature it passes.
 *
 * <p>Nesting is honest — the innermost caller wins and the outer one is restored, so free text typed into the
 * console is recorded as the interpretation it became rather than as the surface it arrived on.
 */
public final class OriginContext {

    private static final ThreadLocal<ActionOrigin> CURRENT = new ThreadLocal<>();

    private OriginContext() {
    }

    /** Attributes everything this thread does until the scope closes; for callers that throw checked exceptions. */
    public static Scope open(ActionOrigin origin) {
        ActionOrigin previous = CURRENT.get();
        CURRENT.set(origin);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public static <T> T as(ActionOrigin origin, Supplier<T> work) {
        try (Scope ignored = open(origin)) {
            return work.get();
        }
    }

    public static void as(ActionOrigin origin, Runnable work) {
        as(origin, () -> {
            work.run();
            return null;
        });
    }

    /** Null when nobody said — a write from a thread no entry point claimed is recorded without an origin. */
    public static ActionOrigin current() {
        return CURRENT.get();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
