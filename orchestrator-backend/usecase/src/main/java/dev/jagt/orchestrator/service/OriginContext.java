package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ActionOrigin;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Carries "who asked" from an entry point down to the write, threading a parameter through putting that question
 * into every signature it passes. Nesting is honest: the innermost caller wins and the outer one is restored.
 */
public final class OriginContext {

    private static final ThreadLocal<ActionOrigin> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> WHY = new ThreadLocal<>();

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

    /** The asker's reason, carried the same way; a null one leaves an outer reason standing. */
    public static <T> T because(@Nullable String why, Supplier<T> work) {
        if (why == null) {
            return work.get();
        }
        String previous = WHY.get();
        WHY.set(why);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                WHY.remove();
            } else {
                WHY.set(previous);
            }
        }
    }

    public static @Nullable String why() {
        return WHY.get();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
