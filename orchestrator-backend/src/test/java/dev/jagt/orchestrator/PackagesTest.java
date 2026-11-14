package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PackagesTest {

  private static final int CEILING = 10;

  @Test
  void everyPackageOnDiskIsInTheMap() {
    assertThat(PackageMap.onDisk().keySet()).isSubsetOf(PackageMap.listed());
  }

  @Test
  void everyPackageTheMapListsExists() {
    assertThat(PackageMap.listed()).isSubsetOf(PackageMap.onDisk().keySet());
  }

  @Test
  void packageHoldsTenClassesAtMostUnlessTheMapNamesIt() {
    assertThat(
            PackageMap.onDisk().entrySet().stream()
                .filter(entry -> entry.getValue() > CEILING)
                .map(Map.Entry::getKey)
                .filter(name -> !PackageMap.overTen().contains(name)))
        .isEmpty();
  }

  @Test
  void packageHoldsMoreThanOneClassUnlessTheMapNamesIt() {
    assertThat(
            PackageMap.onDisk().entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .filter(name -> !PackageMap.single().contains(name)))
        .isEmpty();
  }

  @Test
  void anOverTenExceptionStillHoldsMoreThanTen() {
    assertThat(PackageMap.overTen())
        .allSatisfy(name -> assertThat(PackageMap.onDisk().get(name)).isGreaterThan(CEILING));
  }

  @Test
  void singleClassExceptionStillHoldsOneClass() {
    assertThat(PackageMap.single())
        .allSatisfy(name -> assertThat(PackageMap.onDisk().get(name)).isEqualTo(1L));
  }
}
