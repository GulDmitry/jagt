package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

public final class MainClasses {

  public static final JavaClasses CODE =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(SourceRoots.BASE_PACKAGE);

  private MainClasses() {}
}
