package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verb lookups every typing surface shares. A retired spelling must resolve the same on every machine —
 * lower-casing under a Turkish default locale turns "REVIEW" into "revıew" and would silently send the line to
 * the model instead.
 */
class TaskActionTest {

    @Test
    @ResourceLock(Resources.LOCALE)
    void resolvesARetiredSpellingTypedInAnyCaseOnAnyLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(TaskAction.byRetiredVerb("REVIEW")).contains(TaskAction.SWEEP);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void answersNothingForAWireIdThatIsNoLongerAVerb() {
        assertThat(TaskAction.byId("review")).isEmpty();
    }

    @Test
    void answersNothingForARetiredLookupOfACurrentVerb() {
        assertThat(TaskAction.byRetiredVerb("sweep")).isEmpty();
    }
}
