package technical.maintainability.modularity;

import dev.jagt.orchestrator.PackageMap;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageExceptionsFitness implements Fitness {

  private static final Pattern NUMERAL =
      Pattern.compile(
          "\\d|\\b(two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen"
              + "|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty"
              + "|seventy|eighty|ninety|hundred)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern NAME = Pattern.compile("`([A-Z][A-Za-z0-9]*)`");
  private static final Pattern PRIVATE_LIST =
      Pattern.compile("package-private ((?:`[A-Z][A-Za-z0-9]*`(?:, | and )?)+)");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Map<String, Long> counted = PackageMap.onDisk();
    long smallest =
        PackageMap.overTen().stream()
            .mapToLong(name -> counted.getOrDefault(name, 0L))
            .min()
            .orElse(Long.MAX_VALUE);
    List<String> items = new ArrayList<>();
    PackageMap.overTen().stream()
        .filter(name -> counted.getOrDefault(name, 0L) < function.threshold().limit())
        .map(name -> counted.getOrDefault(name, 0L) + " " + name)
        .forEach(items::add);
    PackageMap.overTenReasons().forEach((name, reason) -> items.addAll(problems(name, reason)));
    List<String> sorted = items.stream().sorted().toList();
    return new Measurement(sorted.isEmpty() ? smallest : 0, sorted);
  }

  private static List<String> problems(String pkg, String reason) {
    List<String> found = new ArrayList<>();
    if (NUMERAL.matcher(reason).find()) {
      found.add(pkg + ": the reason holds a numeral; the test counts");
    }
    Matcher names = NAME.matcher(reason);
    while (names.find()) {
      if (declaration(pkg, names.group(1)).isEmpty()) {
        found.add(pkg + ": " + names.group(1) + " is no class of this package");
      }
    }
    Matcher privates = PRIVATE_LIST.matcher(reason);
    while (privates.find()) {
      Matcher listed = NAME.matcher(privates.group(1));
      while (listed.find()) {
        String name = listed.group(1);
        declaration(pkg, name)
            .filter(line -> line.startsWith("public "))
            .ifPresent(line -> found.add(pkg + ": " + name + " is public"));
      }
    }
    return found;
  }

  private static Optional<String> declaration(String pkg, String name) {
    String tail = "/" + SourceRoots.BASE_PATH + "/" + pkg + "/" + name + ".java";
    Pattern declared =
        Pattern.compile(
            "(?m)^((?:public |final |abstract |sealed |non-sealed )*"
                + "(?:class|record|interface|enum) "
                + name
                + "\\b.*)$");
    return SourceRoots.mainJavaFiles()
        .filter(file -> file.toString().endsWith(tail))
        .map(file -> declared.matcher(SourceRoots.read(file)))
        .filter(Matcher::find)
        .map(match -> match.group(1))
        .findFirst();
  }
}
