package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class EnglishAndUtcTest {

    private static final JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator");

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
        noClasses().should().callMethod(owner, method).check(CODE);
    }
}
