package dev.jagt.orchestrator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

class ProductionConstructorTest {

    @Test
    void everyConstructorIsCalledFromMain() {
        constructors().that().areNotPrivate().and(notCalledFromMain())
                .should(ArchCondition.from(DescribedPredicate.describe(
                        "be a Spring bean's only constructor or a record's canonical one",
                        ProductionConstructorTest::springOrRecordOwns)))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    private static DescribedPredicate<JavaConstructor> notCalledFromMain() {
        return DescribedPredicate.describe("is never called from main",
                constructor -> constructor.getCallsOfSelf().stream()
                        .allMatch(call -> call.getOrigin() instanceof JavaConstructor
                                && call.getOriginOwner().equals(constructor.getOwner())));
    }

    private static boolean springOrRecordOwns(JavaConstructor constructor) {
        JavaClass owner = constructor.getOwner();
        boolean canonical = owner.isRecord()
                && constructor.getRawParameterTypes().size() == owner.getFields().stream().filter(field -> !field.getModifiers().contains(JavaModifier.STATIC)).count();
        boolean theBeansOnly = owner.getConstructors().size() == 1 && (owner.isMetaAnnotatedWith(Component.class)
                || owner.isAnnotatedWith(ConfigurationProperties.class) || owner.isAnnotatedWith(Configuration.class));
        return canonical || theBeansOnly;
    }
}
