package dev.jagt.orchestrator.protocol;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/** How a value on the wire reads: a field left blank and one left out are the same answer. */
final class Wire {

    private Wire() {
    }

    static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    static @Nullable String absent(@Nullable String value) {
        return blank(value) ? null : value;
    }

    /** {@code values} plus the one answer that names none of them. */
    static Collection<String> withNone(Collection<String> values, String none) {
        var allowed = new ArrayList<String>(values);
        allowed.add(none);
        return allowed;
    }
}
