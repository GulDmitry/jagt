package dev.jagt.orchestrator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class EnglishAndUtcTest {

    @ParameterizedTest
    @CsvSource({
            "java.time.ZoneId, systemDefault",
            "java.time.Clock, systemDefaultZone",
            "java.util.TimeZone, getDefault",
            "java.util.Locale, getDefault",
            "java.time.LocalDateTime, now",
            "java.time.LocalDate, now",
            "java.time.LocalTime, now",
            "java.time.ZonedDateTime, now"})
    void noCodeReadsTheMachinesZoneLocaleOrLocalTime(String owner, String method) {
        noClasses().should().callMethod(owner, method).check(MainClasses.CODE);
    }

    @ParameterizedTest
    @CsvSource({
            "java.lang.System, currentTimeMillis",
            "java.time.Clock, systemUTC"})
    void noCodeReadsTheTimeButThroughTheClockBean(String owner, String method) {
        noClasses().that().doNotHaveFullyQualifiedName(RuntimeWiring.class.getName())
                .should().callMethod(owner, method).check(MainClasses.CODE);
    }

    @Test
    void everyJavaTimeNowTakesTheClockBean() {
        noClasses().should().callMethodWhere(DescribedPredicate.describe("a java.time now() not taking a Clock",
                (JavaMethodCall call) -> call.getTarget().getName().equals("now")
                        && call.getTargetOwner().getPackageName().equals("java.time")
                        && call.getTarget().getRawParameterTypes().stream()
                                .noneMatch(type -> type.isEquivalentTo(Clock.class)))).check(MainClasses.CODE);
    }
}
