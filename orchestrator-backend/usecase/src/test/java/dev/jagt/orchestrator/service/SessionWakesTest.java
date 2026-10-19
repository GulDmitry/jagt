package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionWakesTest {

    private static final Instant STOPPED = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant RESET = Instant.parse("2026-10-19T15:00:00Z");

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static UsageLimits reading(double percent, Optional<Instant> resetsAt) {
        SubscriptionUsage usage = () -> new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(
                List.of(new SubscriptionUsage.Window("5h", percent, resetsAt)), Optional.empty()));
        return new UsageLimits(Optional.of(usage));
    }

    private static SessionWakes wakes(UsageLimits limits, boolean on, Path file, Instant now) {
        return new SessionWakes(limits, UsageLimitsProperties.defaults().withWakeOnReset(on), JsonMapper.builder().build(),
                file, at(now));
    }

    @Test
    void wakesNothingBeforeTheWindowResets(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

        List<String> due = wakes(reading(100, Optional.of(RESET)), true, file, RESET.minusSeconds(1)).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void wakesTheSessionOnceWhenTheWindowResets(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        SessionWakes later = wakes(reading(0, Optional.empty()), true, file, RESET);

        List<String> first = later.takeDue(taskId -> false);
        List<String> second = later.takeDue(taskId -> false);

        assertThat(first).containsExactly("ABC-42");
        assertThat(second).isEmpty();
    }

    @Test
    void keepsADueWakePendingWhileTheHumanIsTypingAndSendsItOnceAfter(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        SessionWakes later = wakes(reading(0, Optional.empty()), true, file, RESET);

        List<String> whileTyping = later.takeDue(taskId -> true);
        List<String> afterSending = later.takeDue(taskId -> false);
        List<String> again = later.takeDue(taskId -> false);

        assertThat(whileTyping).isEmpty();
        assertThat(afterSending).containsExactly("ABC-42");
        assertThat(again).isEmpty();
    }

    @Test
    void doesNotWakeASessionTwiceAcrossARestart(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");
        wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false);

        List<String> afterRestart = wakes(reading(0, Optional.empty()), true, file, RESET.plusSeconds(60)).takeDue(taskId -> false);

        assertThat(afterRestart).isEmpty();
    }

    @Test
    void keepsAPendingWakeAcrossARestart(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

        List<String> afterRestart = wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false);

        assertThat(afterRestart).containsExactly("ABC-42");
    }

    @Test
    void armsNoWakeWhenNoWindowIsAtItsLimit(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(99, Optional.of(RESET)), true, file, STOPPED).arm("ABC-42");

        List<String> due = wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void armsNoWakeWhenTheFullWindowReportsNoReset(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.empty()), true, file, STOPPED).arm("ABC-42");

        List<String> due = wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void armsNoWakeWhileTheKeyIsOff(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        wakes(reading(100, Optional.of(RESET)), false, file, STOPPED).arm("ABC-42");

        List<String> due = wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false);

        assertThat(due).isEmpty();
    }

    @Test
    void dropsTheWakeWhenTheSessionMovesAgain(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        SessionWakes armed = wakes(reading(100, Optional.of(RESET)), true, file, STOPPED);
        armed.arm("ABC-42");

        armed.cancel("ABC-42");

        assertThat(wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false)).isEmpty();
    }

    @Test
    void wakesAtTheLaterResetWhenTwoWindowsAreFull(@TempDir Path dir) {
        Path file = dir.resolve("session-wakes.json");
        SubscriptionUsage usage = () -> new SubscriptionUsage.Read(new SubscriptionUsage.Snapshot(List.of(
                new SubscriptionUsage.Window("5h", 100, Optional.of(RESET)),
                new SubscriptionUsage.Window("week", 100, Optional.of(RESET.plusSeconds(86400)))), Optional.empty()));
        wakes(new UsageLimits(Optional.of(usage)), true, file, STOPPED).arm("ABC-42");

        List<String> atFirstReset = wakes(reading(0, Optional.empty()), true, file, RESET).takeDue(taskId -> false);

        assertThat(atFirstReset).isEmpty();
    }
}
